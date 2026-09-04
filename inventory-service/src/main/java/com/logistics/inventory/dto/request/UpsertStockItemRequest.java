package com.logistics.inventory.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Sets an ABSOLUTE stock level, for initial loading or after a physical count.
 *
 * <p>Deliberately distinct from a movement, which is relative. Confusing "the shelf now holds 120"
 * with "add 120 to the shelf" is how stock counts go wrong, so the API makes the two different
 * endpoints with different verbs.
 */
public record UpsertStockItemRequest(

        @NotNull(message = "warehouseId is required")
        UUID warehouseId,

        @NotBlank(message = "productId is required")
        String productId,

        @NotNull(message = "quantityOnHand is required")
        @Min(value = 0, message = "quantityOnHand must be zero or more")
        Integer quantityOnHand,

        @Min(value = 0, message = "reorderThreshold must be zero or more")
        Integer reorderThreshold) {
}
