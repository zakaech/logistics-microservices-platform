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

        @NotNull(message = "warehouseId est obligatoire")
        UUID warehouseId,

        @NotBlank(message = "productId est obligatoire")
        String productId,

        @NotNull(message = "quantityOnHand est obligatoire")
        @Min(value = 0, message = "quantityOnHand doit être supérieur ou égal à zéro")
        Integer quantityOnHand,

        @Min(value = 0, message = "reorderThreshold doit être supérieur ou égal à zéro")
        Integer reorderThreshold) {
}
