package com.logistics.order.dto.response;

import com.logistics.order.domain.enums.OrderStatus;

import java.time.Instant;

/** One transition, for the order timeline. */
public record OrderStatusHistoryResponse(
        OrderStatus fromStatus,
        OrderStatus toStatus,
        String reason,
        String changedBy,
        Instant changedAt) {
}
