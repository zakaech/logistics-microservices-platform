package com.logistics.inventory.service.impl;

import com.logistics.inventory.config.ReservationProperties;
import com.logistics.inventory.domain.entity.Reservation;
import com.logistics.inventory.domain.entity.StockItem;
import com.logistics.inventory.domain.entity.StockMovement;
import com.logistics.inventory.domain.enums.MovementType;
import com.logistics.inventory.domain.enums.ReservationStatus;
import com.logistics.inventory.dto.request.CreateReservationRequest;
import com.logistics.inventory.dto.response.ReservationResponse;
import com.logistics.inventory.exception.InsufficientStockException;
import com.logistics.inventory.exception.InsufficientStockException.Shortage;
import com.logistics.inventory.exception.InvalidReservationStateException;
import com.logistics.inventory.exception.ResourceNotFoundException;
import com.logistics.inventory.mapper.InventoryMapper;
import com.logistics.inventory.repository.ReservationRepository;
import com.logistics.inventory.repository.StockItemRepository;
import com.logistics.inventory.repository.StockMovementRepository;
import com.logistics.inventory.security.CurrentActor;
import com.logistics.inventory.service.ReservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Reservation protocol, and the one place on the platform where concurrency is the whole problem.
 *
 * <h2>Why pessimistic locking here</h2>
 *
 * <p>Stock is contended exactly when it matters. Under optimistic locking, two orders reading the
 * same row both succeed at read time and one fails at commit with a version conflict; the caller
 * retries, re-reads, and may lose again. As the last units of a product sell out, the number of
 * competing transactions rises precisely when the chance of winning falls - the failure rate is
 * worst at the moment the system most needs to behave. Taking {@code SELECT ... FOR UPDATE} instead
 * makes the second transaction wait a few milliseconds and then succeed or fail on the true
 * remaining quantity. One decision, no retry storm.
 *
 * <p>The cost: writers serialise on the contended rows, so throughput per
 * product is bounded by how fast a reservation transaction commits. That is the right trade for
 * stock, where a wrong answer is worse than a slow one. It would be the wrong trade for the
 * catalogue, which is why catalog-service uses optimistic locking instead.
 *
 * <h2>Why the lock order matters</h2>
 *
 * <p>Locks are always acquired in ascending primary-key order (see
 * {@code StockItemRepository.lockAllByIdOrderedById}). Two orders touching the same two rows in
 * opposite orders would each hold what the other waits for, and PostgreSQL would abort one on a
 * deadlock. A single global ordering removes that possibility by construction rather than making it
 * merely unlikely.
 *
 * <h2>What is still true if all of this is wrong</h2>
 *
 * <p>The database enforces {@code quantity_reserved <= quantity_on_hand} as a check constraint. If
 * a lock were ever forgotten, the write is refused rather than overselling. Locking is the
 * strategy; the constraint is the guarantee.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationServiceImpl implements ReservationService {

    private final ReservationRepository reservationRepository;
    private final StockItemRepository stockItemRepository;
    private final StockMovementRepository stockMovementRepository;
    private final ReservationSettlement settlement;
    private final InventoryMapper inventoryMapper;
    private final ReservationProperties reservationProperties;
    private final CurrentActor currentActor;
    private final Clock clock;

    @Override
    @Transactional
    public ReservationResult reserve(CreateReservationRequest request) {
        // Idempotency: the same order number must never hold stock twice. order-service retries
        // this call after a timeout, and a retry that double-reserved would be indistinguishable
        // from a genuine second order.
        var existing = reservationRepository.findByReferenceWithLines(request.reference());
        if (existing.isPresent()) {
            log.info("Reservation '{}' already exists, returning it unchanged", request.reference());
            return new ReservationResult(inventoryMapper.toResponse(existing.get()), false);
        }

        Map<StockKey, Integer> requested = flatten(request);

        // Resolve to row ids first, WITHOUT loading the entities: see the note on findKeys.
        Map<StockKey, UUID> idByKey = resolveStockItemIds(requested.keySet());

        // Then take the locks, in ascending id order.
        List<UUID> ids = idByKey.values().stream().sorted().toList();
        Map<UUID, StockItem> locked = stockItemRepository.lockAllByIdOrderedById(ids).stream()
                .collect(Collectors.toMap(StockItem::getId, item -> item));

        // From here the rows are ours until commit: what is read is what will be written.
        List<Shortage> shortages = new ArrayList<>();
        Map<StockItem, Integer> toReserve = new LinkedHashMap<>();

        requested.forEach((key, quantity) -> {
            StockItem item = locked.get(idByKey.get(key));
            if (item == null || !item.canReserve(quantity)) {
                int available = item == null ? 0 : item.availableQuantity();
                shortages.add(new Shortage(key.warehouseId(), key.productId(), quantity, available));
            } else {
                toReserve.put(item, quantity);
            }
        });

        // All or nothing: a partially reserved order would leave the orchestrator holding stock it
        // cannot use and cannot easily find again.
        if (!shortages.isEmpty()) {
            throw new InsufficientStockException(shortages);
        }

        Instant now = clock.instant();
        Reservation reservation = Reservation.builder()
                .reference(request.reference())
                .status(ReservationStatus.ACTIVE)
                .expiresAt(now.plus(resolveTtl(request.ttlSeconds())))
                .build();

        String actor = currentActor.identifier();
        List<StockMovement> movements = new ArrayList<>();

        toReserve.forEach((item, quantity) -> {
            item.reserve(quantity);
            item.touch(now);
            reservation.addLine(item, quantity);
            movements.add(StockMovement.of(item, MovementType.RESERVATION, quantity,
                    request.reference(), actor, now));
        });

        stockItemRepository.saveAll(toReserve.keySet());
        Reservation saved = reservationRepository.save(reservation);
        stockMovementRepository.saveAll(movements);

        log.info("Reserved {} line(s) for '{}', expiring at {}",
                toReserve.size(), saved.getReference(), saved.getExpiresAt());
        return new ReservationResult(inventoryMapper.toResponse(saved), true);
    }

    @Override
    @Transactional
    public ReservationResponse confirm(UUID id) {
        return settle(id, ReservationStatus.CONFIRMED);
    }

    @Override
    @Transactional
    public ReservationResponse cancel(UUID id) {
        return settle(id, ReservationStatus.CANCELLED);
    }

    /**
     * Confirm and cancel differ only in what happens to the held units, so they share one path.
     *
     * <p>Both re-take the locks in the same ascending id order as reserve. Skipping the lock here
     * because "the stock is already reserved" would be wrong: a concurrent expiry sweep or a manual
     * stock correction touches the same rows.
     */
    private ReservationResponse settle(UUID id, ReservationStatus target) {
        Reservation reservation = reservationRepository.findByIdWithLines(id)
                .orElseThrow(() -> new ResourceNotFoundException("Réservation", id));

        if (reservation.getStatus() != ReservationStatus.ACTIVE) {
            throw new InvalidReservationStateException(reservation.getReference(),
                    reservation.getStatus(),
                    target == ReservationStatus.CONFIRMED ? "confirmed" : "cancelled");
        }

        Instant now = clock.instant();

        // An expired reservation no longer holds anything: the sweeper has given the units back,
        // so confirming it would ship stock that was never actually held.
        if (target == ReservationStatus.CONFIRMED && reservation.isExpiredAt(now)) {
            throw new InvalidReservationStateException(reservation.getReference(),
                    ReservationStatus.EXPIRED, "confirmed");
        }

        settlement.apply(reservation, target, now, currentActor.identifier());
        return inventoryMapper.toResponse(reservation);
    }

    @Override
    @Transactional(readOnly = true)
    public ReservationResponse findById(UUID id) {
        return inventoryMapper.toResponse(reservationRepository.findByIdWithLines(id)
                .orElseThrow(() -> new ResourceNotFoundException("Réservation", id)));
    }

    @Override
    @Transactional(readOnly = true)
    public ReservationResponse findByReference(String reference) {
        return inventoryMapper.toResponse(reservationRepository.findByReferenceWithLines(reference)
                .orElseThrow(() -> new ResourceNotFoundException("Réservation", reference)));
    }

    /**
     * Releases holds whose TTL has passed.
     *
     * <p>Each reservation is expired in its OWN transaction, deliberately. A single transaction
     * over the whole batch would hold locks on every affected stock row for as long as the sweep
     * runs, blocking live reservations; and one bad row would roll back the entire sweep.
     */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int expireOverdueReservations() {
        List<UUID> due = reservationRepository.findIdsByStatusAndExpiresAtBefore(
                ReservationStatus.ACTIVE, clock.instant());

        int expired = 0;
        for (UUID id : due) {
            try {
                if (settlement.expireOne(id)) {
                    expired++;
                }
            } catch (RuntimeException e) {
                // One stuck reservation must not stop the sweep: the next run will try again.
                log.warn("Could not expire reservation {}: {}", id, e.getMessage());
            }
        }
        if (expired > 0) {
            log.info("Expired {} overdue reservation(s)", expired);
        }
        return expired;
    }

    private Duration resolveTtl(Integer ttlSeconds) {
        return ttlSeconds == null
                ? reservationProperties.defaultTtl()
                : Duration.ofSeconds(ttlSeconds);
    }

    /**
     * Flattens the request into one entry per (warehouse, product), summing duplicates.
     *
     * <p>Summing rather than rejecting because a split allocation can legitimately name the same
     * product twice for one warehouse; what must not happen is reserving each occurrence against
     * the same availability check.
     */
    private Map<StockKey, Integer> flatten(CreateReservationRequest request) {
        Map<StockKey, Integer> requested = new LinkedHashMap<>();
        for (CreateReservationRequest.Segment segment : request.segments()) {
            for (CreateReservationRequest.Line line : segment.lines()) {
                requested.merge(new StockKey(segment.warehouseId(), line.productId()),
                        line.quantity(), Integer::sum);
            }
        }
        return requested;
    }

    private Map<StockKey, UUID> resolveStockItemIds(Set<StockKey> keys) {
        Set<UUID> warehouseIds = keys.stream().map(StockKey::warehouseId).collect(Collectors.toSet());
        Set<String> productIds = keys.stream().map(StockKey::productId).collect(Collectors.toSet());

        Map<StockKey, UUID> resolved = stockItemRepository.findKeys(warehouseIds, productIds).stream()
                .collect(Collectors.toMap(
                        key -> new StockKey(key.getWarehouseId(), key.getProductId()),
                        StockItemRepository.StockItemKey::getId,
                        (first, second) -> first));

        // A pair with no stock row is not an error yet: it becomes a shortage of zero available,
        // which is the same answer as "the row exists but is empty" and keeps the caller simple.
        return keys.stream()
                .filter(resolved::containsKey)
                .collect(Collectors.toMap(key -> key, resolved::get, (a, b) -> a, LinkedHashMap::new));
    }

    /** Addresses one stock row: a product in a warehouse. */
    private record StockKey(UUID warehouseId, String productId) {
    }
}
