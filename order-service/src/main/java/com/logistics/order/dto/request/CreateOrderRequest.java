package com.logistics.order.dto.request;

import com.logistics.order.dto.common.DeliveryAddressDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * A new order.
 *
 * <p>Two fields are deliberately absent. There is no {@code customerId}: it comes from the signed
 * token, so nobody can order on someone else's account. And there is no {@code total}: it is
 * computed from the catalogue prices at the moment of the order, so a client cannot name its own
 * price.
 */
public record CreateOrderRequest(

        @NotEmpty(message = "lines must contain at least one product")
        @Size(max = 50, message = "at most 50 lines per order")
        List<@Valid OrderLineRequest> lines,

        @NotNull(message = "deliveryAddress is required")
        @Valid DeliveryAddressDto deliveryAddress,

        /* Optional: falls back to the configured default strategy. */
        String strategy) {

    public record OrderLineRequest(

            @NotBlank(message = "productId is required")
            String productId,

            @NotNull(message = "quantity is required")
            @Min(value = 1, message = "quantity must be at least 1")
            @Max(value = 1000, message = "quantity must not exceed 1000")
            Integer quantity) {
    }
}
