package com.logistics.inventory.dto.request;

import com.logistics.inventory.domain.enums.MovementType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * A RELATIVE change to physical stock.
 *
 * <p>Only the three physical types are accepted here; RESERVATION and RELEASE are written by the
 * reservation flow alone. Letting an operator post them by hand would let the ledger and the
 * reservation table tell different stories.
 *
 * <p>{@code quantity} is always positive: the direction comes from the type, so a typo cannot flip
 * an inbound delivery into a shipment.
 */
public record CreateStockMovementRequest(

        @NotNull(message = "warehouseId is required")
        UUID warehouseId,

        @NotBlank(message = "productId is required")
        String productId,

        @NotNull(message = "type is required")
        MovementType type,

        @NotNull(message = "quantity is required")
        @Min(value = 1, message = "quantity must be at least 1")
        Integer quantity,

        @Size(max = 64, message = "reference must not exceed 64 characters")
        String reference) {
}
