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

        @NotNull(message = "warehouseId est obligatoire")
        UUID warehouseId,

        @NotBlank(message = "productId est obligatoire")
        String productId,

        @NotNull(message = "type est obligatoire")
        MovementType type,

        @NotNull(message = "quantity est obligatoire")
        @Min(value = 1, message = "quantity doit valoir au moins 1")
        Integer quantity,

        @Size(max = 64, message = "reference ne doit pas dépasser 64 caractères")
        String reference) {
}
