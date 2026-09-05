package com.logistics.inventory.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * A hold on stock across one or more warehouses.
 *
 * <p>{@code reference} is the order number and is the <b>idempotency key</b>: replaying this
 * request after a timeout returns the existing reservation instead of holding the stock twice. That
 * is what makes the order saga safe to retry.
 *
 * <p>The request is shaped as segments because an order may be split across warehouses - which is
 * precisely the case the allocation engine exists to produce.
 */
public record CreateReservationRequest(

        @NotBlank(message = "reference est obligatoire")
        @Size(max = 64, message = "reference ne doit pas dépasser 64 caractères")
        String reference,

        /* Absent means the configured default TTL. */
        @Min(value = 30, message = "ttlSeconds doit valoir au moins 30")
        Integer ttlSeconds,

        @NotEmpty(message = "segments doit contenir au moins un entrepôt")
        @Size(max = 20, message = "20 entrepôts au maximum par réservation")
        List<@Valid Segment> segments) {

    public record Segment(

            @NotNull(message = "warehouseId est obligatoire")
            UUID warehouseId,

            @NotEmpty(message = "lines doit contenir au moins un produit")
            @Size(max = 50, message = "50 lignes au maximum par entrepôt")
            List<@Valid Line> lines) {
    }

    public record Line(

            @NotBlank(message = "productId est obligatoire")
            String productId,

            @NotNull(message = "quantity est obligatoire")
            @Min(value = 1, message = "quantity doit valoir au moins 1")
            Integer quantity) {
    }
}
