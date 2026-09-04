package com.logistics.order.dto.request;

import com.logistics.order.domain.enums.OrderStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Warehouse-driven progress: SHIPPED, then DELIVERED. */
public record UpdateOrderStatusRequest(

        @NotNull(message = "status is required")
        OrderStatus status,

        @Size(max = 255, message = "reason must not exceed 255 characters")
        String reason) {
}
