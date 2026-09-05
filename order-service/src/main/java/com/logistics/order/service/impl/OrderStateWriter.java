package com.logistics.order.service.impl;

import com.logistics.order.allocation.model.AllocationPlan;
import com.logistics.order.allocation.model.AllocationSegment;
import com.logistics.order.allocation.model.SegmentLine;
import com.logistics.order.client.dto.ReservationHandle;
import com.logistics.order.domain.entity.Order;
import com.logistics.order.domain.entity.OrderAllocation;
import com.logistics.order.domain.enums.OrderStatus;
import com.logistics.order.dto.response.OrderResponse;
import com.logistics.order.exception.ResourceNotFoundException;
import com.logistics.order.mapper.OrderMapper;
import com.logistics.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * The database writes of the order saga, each in its own short transaction.
 *
 * <p>A separate bean, not private methods, and for a concrete reason. Placing an order spans three
 * HTTP calls; holding one database transaction open across them would keep a connection and its row
 * locks for the duration of someone else's network latency. So each step commits on its own.
 *
 * <p>Spring applies {@code @Transactional} through a proxy, and a call from one method of a class to
 * another of the same class bypasses that proxy entirely. Keeping these methods on a collaborator
 * is what guarantees each one really gets the transaction it declares - the same trap already met
 * in inventory-service.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderStateWriter {

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final Clock clock;

    /**
     * Loads an order and maps it, inside a transaction.
     *
     * <p>Needed because {@code open-in-view} is off - deliberately, since rendering a response is
     * no reason to hold a database connection. The consequence is that an entity handed back from a
     * committed transaction is detached, and touching a lazy collection then throws. Mapping here,
     * while the session is still open, is what keeps the saga free to span HTTP calls without
     * leaving a transaction open across them.
     */
    @Transactional(readOnly = true)
    public OrderResponse responseFor(UUID orderId) {
        Order order = require(orderId);
        // Initialise the nested collections while there is still a session to do it with.
        order.getAllocations().forEach(allocation -> allocation.getLines().size());
        return orderMapper.toResponse(order);
    }

    @Transactional
    public Order save(Order order) {
        return orderRepository.save(order);
    }

    /**
     * Records the plan and the hold. The order now owns stock, so the reservation reference is
     * stored in the same transaction: an order in ALLOCATED without a reference would be a hold
     * nobody could ever release.
     */
    @Transactional
    public Order applyAllocation(UUID orderId, AllocationPlan plan, ReservationHandle handle,
                                 String actor) {
        Order order = require(orderId);
        Instant now = clock.instant();

        order.setAllocationStrategy(plan.strategyName());
        order.setSplitShipment(plan.splitShipment());
        order.setReservationReference(handle.reference());
        order.setReservationId(handle.id());

        for (AllocationSegment segment : plan.segments()) {
            OrderAllocation allocation = OrderAllocation.builder()
                    .warehouseId(segment.warehouseId())
                    .warehouseCode(segment.warehouseCode())
                    .shipmentSequence(segment.shipmentSequence())
                    .distanceKm(BigDecimal.valueOf(segment.distanceKm())
                            .setScale(2, RoundingMode.HALF_UP))
                    .createdAt(now)
                    .build();

            for (SegmentLine line : segment.lines()) {
                allocation.addLine(order.lineForProduct(line.productId()), line.quantity());
            }
            order.addAllocation(allocation);
        }

        order.transitionTo(OrderStatus.ALLOCATED,
                "Affectée par '" + plan.strategyName() + "' sur "
                        + plan.shipmentCount() + " expédition(s)", actor, now);

        return orderRepository.save(order);
    }

    @Transactional
    public Order transition(UUID orderId, OrderStatus target, String reason, String actor) {
        Order order = require(orderId);
        order.transitionTo(target, reason, actor, clock.instant());
        return orderRepository.save(order);
    }

    /**
     * Marks an order rejected in its own transaction.
     *
     * <p>Called on a failure path, so it must not depend on the caller's transaction still being
     * viable - the whole point is to leave a durable record of why the order could not be served.
     */
    @Transactional
    public void markRejected(UUID orderId, String reason, String actor) {
        Order order = require(orderId);
        if (order.getStatus().canTransitionTo(OrderStatus.REJECTED)) {
            order.transitionTo(OrderStatus.REJECTED, reason, actor, clock.instant());
            orderRepository.save(order);
            log.info("Order {} rejected: {}", order.getOrderNumber(), reason);
        }
    }

    /**
     * Cancels on a failure path, without ever throwing.
     *
     * <p>Used while another failure is already being reported. Raising a second exception here
     * would replace a useful error with a confusing one, so an illegal transition is logged and
     * swallowed rather than propagated.
     */
    @Transactional
    public void markCancelledQuietly(UUID orderId, String reason, String actor) {
        try {
            Order order = require(orderId);
            if (order.getStatus().canTransitionTo(OrderStatus.CANCELLED)) {
                order.transitionTo(OrderStatus.CANCELLED, reason, actor, clock.instant());
                orderRepository.save(order);
            }
        } catch (RuntimeException e) {
            log.error("Could not mark order {} cancelled after a failure", orderId, e);
        }
    }

    private Order require(UUID orderId) {
        return orderRepository.findByIdWithDetails(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Commande", orderId));
    }
}
