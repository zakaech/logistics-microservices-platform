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

        @NotBlank(message = "reference is required")
        @Size(max = 64, message = "reference must not exceed 64 characters")
        String reference,

        /* Absent means the configured default TTL. */
        @Min(value = 30, message = "ttlSeconds must be at least 30")
        Integer ttlSeconds,

        @NotEmpty(message = "segments must contain at least one warehouse")
        @Size(max = 20, message = "at most 20 warehouses per reservation")
        List<@Valid Segment> segments) {

    public record Segment(

            @NotNull(message = "warehouseId is required")
            UUID warehouseId,

            @NotEmpty(message = "lines must contain at least one product")
            @Size(max = 50, message = "at most 50 lines per warehouse")
            List<@Valid Line> lines) {
    }

    public record Line(

            @NotBlank(message = "productId is required")
            String productId,

            @NotNull(message = "quantity is required")
            @Min(value = 1, message = "quantity must be at least 1")
            Integer quantity) {
    }
}
