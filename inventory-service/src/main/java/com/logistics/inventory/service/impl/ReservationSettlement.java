package com.logistics.inventory.service.impl;

import com.logistics.inventory.domain.entity.Reservation;
import com.logistics.inventory.domain.entity.ReservationLine;
import com.logistics.inventory.domain.entity.StockItem;
import com.logistics.inventory.domain.entity.StockMovement;
import com.logistics.inventory.domain.enums.MovementType;
import com.logistics.inventory.domain.enums.ReservationStatus;
import com.logistics.inventory.repository.ReservationRepository;
import com.logistics.inventory.repository.StockItemRepository;
import com.logistics.inventory.repository.StockMovementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * What happens to stock when a reservation reaches a terminal state.
 *
 * <p>Confirm, cancel and expire differ only in the effect on the held units, so the effect lives in
 * one place, so the release logic cannot drift between callers.
 *
 * <p>It is a separate bean rather than a private method for a concrete reason. The expiry sweep
 * needs each reservation in its OWN transaction, and Spring applies {@code @Transactional} through
 * a proxy - a call from one method of a class to another of the same class bypasses that proxy
 * entirely and would silently run in the caller's transaction, which the sweep must avoid.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationSettlement {

    private final ReservationRepository reservationRepository;
    private final StockItemRepository stockItemRepository;
    private final StockMovementRepository stockMovementRepository;
    private final Clock clock;

    /**
     * Applies the stock effect of a terminal transition and records it in the ledger.
     *
     * <p>Takes the row locks in ascending id order, the same order every other path uses. Skipping
     * the lock because "these units are already reserved" would be wrong: a concurrent stock
     * correction or expiry sweep touches the same rows.
     *
     * <p>No transaction annotation: it always runs inside the caller's transaction, so the stock
     * update, the state change and the ledger entries commit together or not at all.
     */
    public void apply(Reservation reservation, ReservationStatus target, Instant now, String actor) {
        List<UUID> ids = reservation.getLines().stream()
                .map(line -> line.getStockItem().getId())
                .sorted()
                .toList();

        Map<UUID, StockItem> locked = stockItemRepository.lockAllByIdOrderedById(ids).stream()
                .collect(Collectors.toMap(StockItem::getId, item -> item));

        List<StockMovement> movements = new ArrayList<>();

        for (ReservationLine line : reservation.getLines()) {
            StockItem item = locked.get(line.getStockItem().getId());
            int quantity = line.getQuantity();

            if (target == ReservationStatus.CONFIRMED) {
                // On-hand and reserved drop together, so the invariant holds at every instant.
                item.shipReserved(quantity);
                movements.add(StockMovement.of(item, MovementType.OUTBOUND, -quantity,
                        reservation.getReference(), actor, now));
            } else {
                item.release(quantity);
                movements.add(StockMovement.of(item, MovementType.RELEASE, -quantity,
                        reservation.getReference(), actor, now));
            }
            item.touch(now);
        }

        stockItemRepository.saveAll(locked.values());
        reservation.transitionTo(target);
        reservationRepository.save(reservation);
        stockMovementRepository.saveAll(movements);

        log.info("Reservation '{}' -> {} ({} line(s))",
                reservation.getReference(), target, reservation.getLines().size());
    }

    /**
     * Expires one overdue reservation in its own transaction.
     *
     * @return true if it was expired, false if it had already been settled meanwhile
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean expireOne(UUID id) {
        Reservation reservation = reservationRepository.findByIdWithLines(id).orElse(null);

        // Re-checked inside the transaction: order-service may have confirmed it since the sweep
        // listed it as due.
        if (reservation == null || reservation.getStatus() != ReservationStatus.ACTIVE) {
            return false;
        }
        apply(reservation, ReservationStatus.EXPIRED, clock.instant(), "system");
        return true;
    }
}
