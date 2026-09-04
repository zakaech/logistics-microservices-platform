package com.logistics.order.dto.response;

import com.logistics.order.domain.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Listing view: what a table of orders shows, and nothing more. */
public record OrderSummaryResponse(
        UUID id,
        String orderNumber,
        OrderStatus status,
        BigDecimal totalAmount,
        String currency,
        int lineCount,
        boolean splitShipment,
        Instant createdAt) {
}
