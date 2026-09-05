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

        @NotEmpty(message = "lines doit contenir au moins un produit")
        @Size(max = 50, message = "50 lignes au maximum par commande")
        List<@Valid OrderLineRequest> lines,

        @NotNull(message = "deliveryAddress est obligatoire")
        @Valid DeliveryAddressDto deliveryAddress,

        /* Optional: falls back to the configured default strategy. */
        String strategy) {

    public record OrderLineRequest(

            @NotBlank(message = "productId est obligatoire")
            String productId,

            @NotNull(message = "quantity est obligatoire")
            @Min(value = 1, message = "quantity doit valoir au moins 1")
            @Max(value = 1000, message = "quantity ne doit pas dépasser 1000")
            Integer quantity) {
    }
}
