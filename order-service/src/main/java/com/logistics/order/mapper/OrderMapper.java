package com.logistics.order.mapper;

import com.logistics.order.domain.entity.Order;
import com.logistics.order.domain.entity.OrderAllocation;
import com.logistics.order.domain.entity.OrderLine;
import com.logistics.order.domain.entity.OrderStatusHistory;
import com.logistics.order.domain.vo.DeliveryAddress;
import com.logistics.order.dto.common.DeliveryAddressDto;
import com.logistics.order.dto.response.OrderAllocationResponse;
import com.logistics.order.dto.response.OrderLineResponse;
import com.logistics.order.dto.response.OrderResponse;
import com.logistics.order.dto.response.OrderStatusHistoryResponse;
import com.logistics.order.dto.response.OrderSummaryResponse;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * Entity to DTO translation.
 *
 * <p>Hand-written rather than generated: the responses deliberately expose different shapes of the
 * same aggregate, and the allocation view flattens a two-level structure. Generator annotations
 * would express that as expression strings the compiler cannot check.
 */
@Component
public class OrderMapper {

    public OrderResponse toResponse(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getStatus(),
                order.getCustomerId(),
                order.totalAmount(),
                order.getTotal() == null ? null : order.getTotal().getCurrency(),
                toAddressDto(order.getDeliveryAddress()),
                order.getAllocationStrategy(),
                order.isSplitShipment(),
                order.getLines().stream().map(this::toLineResponse).toList(),
                order.getAllocations().stream()
                        .sorted(Comparator.comparingInt(OrderAllocation::getShipmentSequence))
                        .map(this::toAllocationResponse)
                        .toList(),
                order.getCreatedAt(),
                order.getUpdatedAt());
    }

    public OrderSummaryResponse toSummary(Order order) {
        return new OrderSummaryResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getStatus(),
                order.totalAmount(),
                order.getTotal() == null ? null : order.getTotal().getCurrency(),
                order.getLines().size(),
                order.isSplitShipment(),
                order.getCreatedAt());
    }

    public List<OrderStatusHistoryResponse> toHistory(Order order) {
        return order.getStatusHistory().stream()
                .sorted(Comparator.comparing(OrderStatusHistory::getChangedAt))
                .map(entry -> new OrderStatusHistoryResponse(
                        entry.getFromStatus(),
                        entry.getToStatus(),
                        entry.getReason(),
                        entry.getChangedBy(),
                        entry.getChangedAt()))
                .toList();
    }

    public List<OrderAllocationResponse> toAllocations(Order order) {
        return order.getAllocations().stream()
                .sorted(Comparator.comparingInt(OrderAllocation::getShipmentSequence))
                .map(this::toAllocationResponse)
                .toList();
    }

    public DeliveryAddress toDeliveryAddress(DeliveryAddressDto dto) {
        return new DeliveryAddress(dto.line1(), dto.city(), dto.postalCode(), dto.country(),
                dto.latitude(), dto.longitude());
    }

    private OrderLineResponse toLineResponse(OrderLine line) {
        return new OrderLineResponse(
                line.getId(),
                line.getProductId(),
                line.getProductSku(),
                line.getProductName(),
                line.getQuantity(),
                line.getUnitPrice().getAmount(),
                line.getLineTotal(),
                line.getUnitPrice().getCurrency());
    }

    private OrderAllocationResponse toAllocationResponse(OrderAllocation allocation) {
        return new OrderAllocationResponse(
                allocation.getWarehouseId(),
                allocation.getWarehouseCode(),
                allocation.getShipmentSequence(),
                allocation.getDistanceKm(),
                allocation.getLines().stream()
                        .map(line -> new OrderAllocationResponse.AllocationLineResponse(
                                line.getOrderLine().getId(),
                                line.getOrderLine().getProductId(),
                                line.getQuantity()))
                        .toList());
    }

    private DeliveryAddressDto toAddressDto(DeliveryAddress address) {
        return address == null ? null : new DeliveryAddressDto(
                address.getLine1(), address.getCity(), address.getPostalCode(),
                address.getCountry(), address.getLatitude(), address.getLongitude());
    }
}
