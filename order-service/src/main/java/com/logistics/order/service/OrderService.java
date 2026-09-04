package com.logistics.order.service;

import com.logistics.order.dto.request.CancelOrderRequest;
import com.logistics.order.dto.request.CreateOrderRequest;
import com.logistics.order.domain.enums.OrderStatus;
import com.logistics.order.dto.response.OrderAllocationResponse;
import com.logistics.order.dto.response.OrderResponse;
import com.logistics.order.dto.response.OrderStatusHistoryResponse;
import com.logistics.order.dto.response.OrderSummaryResponse;
import com.logistics.order.dto.response.PagedResponse;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Order use cases. */
public interface OrderService {

    /**
     * Places an order: resolve the catalogue, price it, allocate, reserve, confirm.
     *
     * <p>The orchestration side of the saga. Every failure has a defined outcome - a rejected order
     * with a reason, or a released hold - rather than a half-finished write.
     */
    OrderResponse create(CreateOrderRequest request, UUID customerId, String actor);

    OrderResponse findById(UUID id, UUID callerId, boolean privileged);

    PagedResponse<OrderSummaryResponse> search(UUID customerId, OrderStatus status,
                                               Instant from, Instant to, Pageable pageable);

    List<OrderAllocationResponse> findAllocations(UUID id, UUID callerId, boolean privileged);

    List<OrderStatusHistoryResponse> findHistory(UUID id, UUID callerId, boolean privileged);

    /** Cancels and, if stock is still held, releases it. */
    OrderResponse cancel(UUID id, CancelOrderRequest request, UUID callerId, boolean privileged,
                         String actor);

    /** Warehouse-driven progress: SHIPPED, then DELIVERED. */
    OrderResponse changeStatus(UUID id, OrderStatus target, String reason, String actor);
}
