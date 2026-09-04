package com.logistics.inventory.dto.response;

import com.logistics.inventory.domain.enums.MovementType;

import java.time.Instant;
import java.util.UUID;

/** One entry of the append-only ledger. */
public record StockMovementResponse(
        UUID id,
        UUID warehouseId,
        String warehouseCode,
        String productId,
        MovementType type,
        int quantity,
        String reference,
        String createdBy,
        Instant occurredAt) {
}
