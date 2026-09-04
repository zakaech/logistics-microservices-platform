package com.logistics.order.dto.response;

import com.logistics.order.domain.enums.OrderStatus;
import com.logistics.order.dto.common.DeliveryAddressDto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Full order view, including the shipments the engine decided on. */
public record OrderResponse(
        UUID id,
        String orderNumber,
        OrderStatus status,
        UUID customerId,
        BigDecimal totalAmount,
        String currency,
        DeliveryAddressDto deliveryAddress,
        String allocationStrategy,
        boolean splitShipment,
        List<OrderLineResponse> lines,
        List<OrderAllocationResponse> allocations,
        Instant createdAt,
        Instant updatedAt) {
}
