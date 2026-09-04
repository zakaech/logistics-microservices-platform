package com.logistics.inventory.dto.response;

import java.time.Instant;
import java.util.UUID;

/**
 * A stock level.
 *
 * <p>{@code availableQuantity} and {@code lowStock} are computed on the way out, never stored:
 * persisting them would create values that can disagree with the two they derive from.
 */
public record StockItemResponse(
        UUID id,
        UUID warehouseId,
        String warehouseCode,
        String productId,
        int quantityOnHand,
        int quantityReserved,
        int availableQuantity,
        int reorderThreshold,
        boolean lowStock,
        Instant updatedAt) {
}
