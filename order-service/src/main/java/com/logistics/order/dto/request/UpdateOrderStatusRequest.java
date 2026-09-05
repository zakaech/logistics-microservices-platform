package com.logistics.order.dto.request;

import com.logistics.order.domain.enums.OrderStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Warehouse-driven progress: SHIPPED, then DELIVERED. */
public record UpdateOrderStatusRequest(

        @NotNull(message = "status est obligatoire")
        OrderStatus status,

        @Size(max = 255, message = "reason ne doit pas dépasser 255 caractères")
        String reason) {
}
