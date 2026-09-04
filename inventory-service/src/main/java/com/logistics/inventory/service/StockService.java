package com.logistics.inventory.service;

import com.logistics.inventory.domain.enums.MovementType;
import com.logistics.inventory.dto.request.AvailabilityRequest;
import com.logistics.inventory.dto.request.CreateStockMovementRequest;
import com.logistics.inventory.dto.request.UpsertStockItemRequest;
import com.logistics.inventory.dto.response.AvailabilityResponse;
import com.logistics.inventory.dto.response.PagedResponse;
import com.logistics.inventory.dto.response.StockItemResponse;
import com.logistics.inventory.dto.response.StockMovementResponse;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.UUID;

/** Stock levels, the movement ledger, and the availability snapshot. */
public interface StockService {

    PagedResponse<StockItemResponse> findStock(UUID warehouseId, String productId, Pageable pageable);

    PagedResponse<StockItemResponse> findByWarehouse(UUID warehouseId, boolean lowStockOnly,
                                                     Pageable pageable);

    StockItemResponse findOne(UUID warehouseId, String productId);

    /** Sets an absolute level; creates the row if this product is new to the warehouse. */
    StockItemResponse upsert(UpsertStockItemRequest request);

    /** Applies a relative physical movement and records it in the ledger. */
    StockMovementResponse recordMovement(CreateStockMovementRequest request);

    PagedResponse<StockMovementResponse> findMovements(UUID warehouseId, String productId,
                                                       MovementType type, Instant from, Instant to,
                                                       Pageable pageable);

    /** Stock and coordinates for a set of products, across active warehouses only. */
    AvailabilityResponse availability(AvailabilityRequest request);
}
