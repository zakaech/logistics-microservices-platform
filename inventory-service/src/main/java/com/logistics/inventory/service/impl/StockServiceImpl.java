package com.logistics.inventory.service.impl;

import com.logistics.inventory.domain.entity.StockItem;
import com.logistics.inventory.domain.entity.StockMovement;
import com.logistics.inventory.domain.entity.Warehouse;
import com.logistics.inventory.domain.enums.MovementType;
import com.logistics.inventory.dto.request.AvailabilityRequest;
import com.logistics.inventory.dto.request.CreateStockMovementRequest;
import com.logistics.inventory.dto.request.UpsertStockItemRequest;
import com.logistics.inventory.dto.response.AvailabilityResponse;
import com.logistics.inventory.dto.response.PagedResponse;
import com.logistics.inventory.dto.response.StockItemResponse;
import com.logistics.inventory.dto.response.StockMovementResponse;
import com.logistics.inventory.exception.ResourceNotFoundException;
import com.logistics.inventory.mapper.InventoryMapper;
import com.logistics.inventory.repository.StockItemRepository;
import com.logistics.inventory.repository.StockMovementRepository;
import com.logistics.inventory.repository.spec.StockMovementSpecifications;
import com.logistics.inventory.security.CurrentActor;
import com.logistics.inventory.service.StockService;
import com.logistics.inventory.service.WarehouseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockServiceImpl implements StockService {

    /**
     * Only physical movements may be posted by an operator. RESERVATION and RELEASE are written by
     * the reservation flow alone, so the ledger and the reservation table cannot disagree.
     */
    private static final Set<MovementType> OPERATOR_POSTABLE =
            EnumSet.of(MovementType.INBOUND, MovementType.OUTBOUND, MovementType.ADJUSTMENT);

    private final StockItemRepository stockItemRepository;
    private final StockMovementRepository stockMovementRepository;
    private final WarehouseService warehouseService;
    private final InventoryMapper inventoryMapper;
    private final CurrentActor currentActor;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<StockItemResponse> findStock(UUID warehouseId, String productId,
                                                      Pageable pageable) {
        Page<StockItem> page;
        if (warehouseId != null) {
            page = stockItemRepository.findByWarehouseId(warehouseId, pageable);
        } else if (productId != null && !productId.isBlank()) {
            page = stockItemRepository.findByProductId(productId, pageable);
        } else {
            page = stockItemRepository.findAll(pageable);
        }
        return PagedResponse.from(page, inventoryMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<StockItemResponse> findByWarehouse(UUID warehouseId, boolean lowStockOnly,
                                                            Pageable pageable) {
        warehouseService.requireById(warehouseId);
        Page<StockItem> page = lowStockOnly
                ? stockItemRepository.findLowStockByWarehouse(warehouseId, pageable)
                : stockItemRepository.findByWarehouseId(warehouseId, pageable);
        return PagedResponse.from(page, inventoryMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public StockItemResponse findOne(UUID warehouseId, String productId) {
        return inventoryMapper.toResponse(requireStockItem(warehouseId, productId));
    }

    @Override
    @Transactional
    public StockItemResponse upsert(UpsertStockItemRequest request) {
        Warehouse warehouse = warehouseService.requireById(request.warehouseId());
        Instant now = clock.instant();

        StockItem item = stockItemRepository
                .findByWarehouseIdAndProductId(warehouse.getId(), request.productId())
                .orElseGet(() -> StockItem.builder()
                        .warehouse(warehouse)
                        .productId(request.productId())
                        .quantityOnHand(0)
                        .quantityReserved(0)
                        .reorderThreshold(0)
                        .updatedAt(now)
                        .build());

        int previousOnHand = item.getQuantityOnHand();
        // Refuses to drop below what is already reserved; the domain guard explains why.
        item.setOnHandTo(request.quantityOnHand());
        if (request.reorderThreshold() != null) {
            item.setReorderThreshold(request.reorderThreshold());
        }
        item.touch(now);

        StockItem saved = stockItemRepository.save(item);

        // An absolute correction still leaves a trace: the ledger must explain every change.
        int delta = saved.getQuantityOnHand() - previousOnHand;
        if (delta != 0) {
            stockMovementRepository.save(StockMovement.of(saved, MovementType.ADJUSTMENT, delta,
                    "stock-count", currentActor.identifier(), now));
        }

        return inventoryMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public StockMovementResponse recordMovement(CreateStockMovementRequest request) {
        if (!OPERATOR_POSTABLE.contains(request.type())) {
            throw new IllegalArgumentException("Le type de mouvement " + request.type()
                    + " est écrit par le flux de réservation et ne peut pas être saisi directement.");
        }

        StockItem item = requireStockItem(request.warehouseId(), request.productId());
        Instant now = clock.instant();

        // The request carries a positive quantity; the type decides the direction, so a typo
        // cannot silently turn a delivery into a shipment.
        int signed = (request.type() == MovementType.OUTBOUND)
                ? -request.quantity()
                : request.quantity();

        if (request.type() == MovementType.INBOUND) {
            item.receive(request.quantity());
        } else {
            item.adjust(signed);
        }
        item.touch(now);
        StockItem saved = stockItemRepository.save(item);

        StockMovement movement = stockMovementRepository.save(StockMovement.of(
                saved, request.type(), signed, request.reference(), currentActor.identifier(), now));

        log.info("Recorded {} of {} for product {} in warehouse {}",
                request.type(), request.quantity(), request.productId(), request.warehouseId());
        return inventoryMapper.toResponse(movement);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<StockMovementResponse> findMovements(UUID warehouseId, String productId,
                                                              MovementType type, Instant from,
                                                              Instant to, Pageable pageable) {
        Specification<StockMovement> specification = Specification
                .where(StockMovementSpecifications.inWarehouse(warehouseId))
                .and(StockMovementSpecifications.forProduct(productId))
                .and(StockMovementSpecifications.ofType(type))
                .and(StockMovementSpecifications.occurredFrom(from))
                .and(StockMovementSpecifications.occurredUntil(to));

        return PagedResponse.from(stockMovementRepository.findAll(specification, pageable),
                inventoryMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public AvailabilityResponse availability(AvailabilityRequest request) {
        List<StockItem> items = stockItemRepository.findAvailabilityFor(request.productIds());

        // Grouped by warehouse so the caller gets one entry per candidate site, each carrying its
        // coordinates: everything the allocation engine needs, in a single response.
        Map<Warehouse, Map<String, Integer>> byWarehouse = new LinkedHashMap<>();
        for (StockItem item : items) {
            int available = item.availableQuantity();
            // A site with nothing left to give is not a candidate; omitting it keeps the payload
            // to the warehouses the engine can actually choose.
            if (available <= 0) {
                continue;
            }
            byWarehouse
                    .computeIfAbsent(item.getWarehouse(), warehouse -> new LinkedHashMap<>())
                    .put(item.getProductId(), available);
        }

        List<AvailabilityResponse.WarehouseAvailability> warehouses = byWarehouse.entrySet().stream()
                .map(entry -> new AvailabilityResponse.WarehouseAvailability(
                        entry.getKey().getId(),
                        entry.getKey().getCode(),
                        entry.getKey().getLocation().getLatitude(),
                        entry.getKey().getLocation().getLongitude(),
                        entry.getValue()))
                .toList();

        return new AvailabilityResponse(warehouses);
    }

    private StockItem requireStockItem(UUID warehouseId, String productId) {
        return stockItemRepository.findByWarehouseIdAndProductId(warehouseId, productId)
                .orElseThrow(() -> new ResourceNotFoundException("Ligne de stock",
                        productId + " in warehouse " + warehouseId));
    }
}
