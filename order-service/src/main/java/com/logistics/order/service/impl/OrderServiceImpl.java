package com.logistics.order.service.impl;

import com.logistics.order.allocation.AllocationStrategyResolver;
import com.logistics.order.allocation.WarehouseAllocationStrategy;
import com.logistics.order.allocation.model.AllocationPlan;
import com.logistics.order.allocation.model.AllocationRequest;
import com.logistics.order.allocation.model.AllocationSegment;
import com.logistics.order.allocation.model.RequestedLine;
import com.logistics.order.allocation.model.SegmentLine;
import com.logistics.order.allocation.model.WarehouseCandidate;
import com.logistics.order.client.CatalogClient;
import com.logistics.order.client.InventoryClient;
import com.logistics.order.client.dto.ProductSnapshot;
import com.logistics.order.client.dto.ReservationCommand;
import com.logistics.order.client.dto.ReservationHandle;
import com.logistics.order.config.AllocationProperties;
import com.logistics.order.domain.entity.Order;
import com.logistics.order.domain.entity.OrderLine;
import com.logistics.order.domain.enums.OrderStatus;
import com.logistics.order.domain.vo.Money;
import com.logistics.order.dto.request.CancelOrderRequest;
import com.logistics.order.dto.request.CreateOrderRequest;
import com.logistics.order.dto.response.OrderAllocationResponse;
import com.logistics.order.dto.response.OrderResponse;
import com.logistics.order.dto.response.OrderStatusHistoryResponse;
import com.logistics.order.dto.response.OrderSummaryResponse;
import com.logistics.order.dto.response.PagedResponse;
import com.logistics.order.exception.AllocationFailedException;
import com.logistics.order.exception.ProductUnavailableException;
import com.logistics.order.exception.ResourceNotFoundException;
import com.logistics.order.exception.UpstreamServiceException;
import com.logistics.order.mapper.OrderMapper;
import com.logistics.order.repository.OrderRepository;
import com.logistics.order.repository.spec.OrderSpecifications;
import com.logistics.order.service.OrderNumberGenerator;
import com.logistics.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Places orders, and orchestrates the saga that makes an order real.
 *
 * <p>Six steps, each with a defined failure: resolve the catalogue, price the order, persist it,
 * allocate, reserve, confirm. What matters is that no failure leaves a half-finished state - an
 * order is either CONFIRMED, or REJECTED with a reason, or CANCELLED with its hold released.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderStateWriter stateWriter;
    private final OrderNumberGenerator orderNumberGenerator;
    private final CatalogClient catalogClient;
    private final InventoryClient inventoryClient;
    private final AllocationStrategyResolver strategyResolver;
    private final AllocationProperties allocationProperties;
    private final OrderMapper orderMapper;
    private final Clock clock;

    @Override
    public OrderResponse create(CreateOrderRequest request, UUID customerId, String actor) {
        Map<String, Integer> quantities = flattenLines(request);

        // Step 1: resolve the catalogue. One batch call, not one per product.
        Map<String, ProductSnapshot> products = catalogClient.fetchProducts(quantities.keySet());
        assertAllProductsUsable(quantities.keySet(), products);

        // Steps 2 and 3: price it from the snapshot and persist it before anything else happens.
        // Persisting first is what allows a failed allocation to leave a REJECTED order with a
        // reason, rather than a request that simply vanished.
        Order order = stateWriter.save(buildOrder(request, quantities, products, customerId, actor));
        log.info("Order {} created with {} line(s)", order.getOrderNumber(), order.getLines().size());

        // Steps 4 and 5: allocate and hold the stock.
        AllocatedOrder allocated = allocateAndReserve(order, request.strategy(), actor);

        // Step 6: consume the hold.
        return confirm(allocated, actor);
    }

    /** An order plus the hold taken for it, carried between the two halves of the saga. */
    private record AllocatedOrder(Order order, ReservationHandle handle) {
    }

    /**
     * Reads availability, plans, and takes the hold - retrying once if stock moved in between.
     *
     * <p>The retry exists because availability is a snapshot, not a lock: another order can consume
     * the same units between reading and reserving. It is bounded, deliberately. Retrying
     * indefinitely under contention turns a stockout into a stampede, and after a second refusal
     * the honest answer is that the stock is genuinely gone.
     */
    private AllocatedOrder allocateAndReserve(Order order, String requestedStrategy, String actor) {
        WarehouseAllocationStrategy strategy = strategyResolver.resolve(requestedStrategy);
        Set<String> productIds = order.getLines().stream()
                .map(OrderLine::getProductId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        int attempts = Math.max(1, allocationProperties.maxAllocationAttempts());

        for (int attempt = 1; attempt <= attempts; attempt++) {
            List<WarehouseCandidate> candidates = inventoryClient.fetchAvailability(productIds);

            AllocationPlan plan;
            try {
                plan = strategy.allocate(new AllocationRequest(
                        toRequestedLines(order), order.getDeliveryAddress().location(), candidates));
            } catch (AllocationFailedException e) {
                // The network genuinely cannot serve this. Record why, then tell the caller.
                stateWriter.markRejected(order.getId(),
                        "Aucune combinaison d'entrepôts ne peut satisfaire cette commande", actor);
                throw e;
            }

            try {
                ReservationHandle handle = inventoryClient.reserve(toReservationCommand(order, plan));
                Order allocated = stateWriter.applyAllocation(order.getId(), plan, handle, actor);
                return new AllocatedOrder(allocated, handle);

            } catch (AllocationFailedException e) {
                // Stock moved since the snapshot. Re-read and re-plan, or give up if this was the
                // last attempt.
                if (attempt == attempts) {
                    stateWriter.markRejected(order.getId(),
                            "Le stock a été pris par une autre commande pendant la validation de celle-ci", actor);
                    throw e;
                }
                log.info("Reservation for {} lost a race, re-planning (attempt {}/{})",
                        order.getOrderNumber(), attempt + 1, attempts);
            }
        }

        // Unreachable: the loop either returns or throws on its final attempt.
        throw new IllegalStateException("Allocation loop ended without a decision.");
    }

    /**
     * Consumes the hold.
     *
     * <p>If confirmation fails, the hold is released rather than left to rot. That compensating
     * cancel is the whole reason reserving and confirming are two steps: without it, a failure here
     * would strand the stock until the TTL expired, invisible to every other order in the meantime.
     */
    private OrderResponse confirm(AllocatedOrder allocated, String actor) {
        Order order = allocated.order();
        try {
            inventoryClient.confirm(allocated.handle().id());
        } catch (UpstreamServiceException e) {
            compensate(allocated, actor);
            throw e;
        }

        stateWriter.transition(order.getId(), OrderStatus.CONFIRMED,
                "Stock confirmé et transmis aux entrepôts", actor);
        log.info("Order {} confirmed", order.getOrderNumber());
        return stateWriter.responseFor(order.getId());
    }

    /**
     * Releases a hold this order can no longer use.
     *
     * <p>Failing to compensate is logged, not rethrown: the caller already has a failure to report,
     * and inventory-service expires the hold on its own TTL. Replacing a useful error with a
     * secondary one would only obscure what actually went wrong.
     */
    private void compensate(AllocatedOrder allocated, String actor) {
        try {
            inventoryClient.cancel(allocated.handle().id());
            log.info("Released the hold for {} after a failed confirmation",
                    allocated.order().getOrderNumber());
        } catch (RuntimeException compensationFailure) {
            log.error("Could not release the hold '{}' for order {}; it will expire on its TTL",
                    allocated.handle().reference(), allocated.order().getOrderNumber(),
                    compensationFailure);
        }
        stateWriter.markCancelledQuietly(allocated.order().getId(),
                "Le stock n'a pas pu être confirmé", actor);
    }

    // --- reads --------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public OrderResponse findById(UUID id, UUID callerId, boolean privileged) {
        return orderMapper.toResponse(requireVisible(id, callerId, privileged));
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<OrderSummaryResponse> search(UUID customerId, OrderStatus status,
                                                      Instant from, Instant to, Pageable pageable) {
        Specification<Order> specification = Specification
                .where(OrderSpecifications.ownedBy(customerId))
                .and(OrderSpecifications.hasStatus(status))
                .and(OrderSpecifications.createdFrom(from))
                .and(OrderSpecifications.createdUntil(to));

        return PagedResponse.from(orderRepository.findAll(specification, pageable),
                orderMapper::toSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderAllocationResponse> findAllocations(UUID id, UUID callerId, boolean privileged) {
        return orderMapper.toAllocations(requireVisible(id, callerId, privileged));
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderStatusHistoryResponse> findHistory(UUID id, UUID callerId, boolean privileged) {
        Order order = orderRepository.findByIdWithHistory(id)
                .orElseThrow(() -> new ResourceNotFoundException("Commande", id));
        assertVisible(order, callerId, privileged);
        return orderMapper.toHistory(order);
    }

    // --- lifecycle ----------------------------------------------------------

    @Override
    public OrderResponse cancel(UUID id, CancelOrderRequest request, UUID callerId,
                                boolean privileged, String actor) {
        Order order = requireVisible(id, callerId, privileged);
        String reason = (request == null || request.reason() == null || request.reason().isBlank())
                ? "Annulée à la demande"
                : request.reason();

        // Releasing the hold BEFORE recording the cancellation. If the release fails, the order
        // stays as it was and the caller sees an error - which is recoverable. The reverse order
        // would leave a cancelled order still silently holding stock.
        if (order.getStatus().holdsStock() && order.getReservationId() != null) {
            inventoryClient.cancel(order.getReservationId());
            log.info("Released the hold for cancelled order {}", order.getOrderNumber());
        } else if (order.getStatus().stockHasLeft()) {
            // The goods are gone: giving them back is a physical return, recorded by a warehouse
            // operator as an inbound movement, not something this service can decide alone.
            log.info("Order {} is cancelled after dispatch; stock must be returned physically",
                    order.getOrderNumber());
        }

        stateWriter.transition(id, OrderStatus.CANCELLED, reason, actor);
        return stateWriter.responseFor(id);
    }

    @Override
    public OrderResponse changeStatus(UUID id, OrderStatus target, String reason, String actor) {
        // Only forward progress belongs on this endpoint. Cancellation has its own, because it has
        // a compensating action; allocation and confirmation are the saga's, not an operator's.
        if (target != OrderStatus.SHIPPED && target != OrderStatus.DELIVERED) {
            throw new IllegalArgumentException(
                    "Only SHIPPED and DELIVERED can be set here; got " + target + ".");
        }
        stateWriter.transition(id, target, reason, actor);
        return stateWriter.responseFor(id);
    }

    // --- helpers ------------------------------------------------------------

    /**
     * Merges the request into one entry per product.
     *
     * <p>A duplicated product is rejected rather than summed: two lines for the same item almost
     * always means a client-side bug, and quietly merging them would hide it while doubling what
     * the customer is charged.
     */
    private Map<String, Integer> flattenLines(CreateOrderRequest request) {
        Map<String, Integer> quantities = new LinkedHashMap<>();
        for (CreateOrderRequest.OrderLineRequest line : request.lines()) {
            if (quantities.putIfAbsent(line.productId(), line.quantity()) != null) {
                throw new IllegalArgumentException(
                        "Product " + line.productId() + " appears on more than one line.");
            }
        }
        return quantities;
    }

    private void assertAllProductsUsable(Set<String> requested,
                                         Map<String, ProductSnapshot> resolved) {
        List<ProductUnavailableException.UnavailableProduct> unavailable = new ArrayList<>();

        for (String productId : requested) {
            ProductSnapshot snapshot = resolved.get(productId);
            if (snapshot == null) {
                unavailable.add(ProductUnavailableException.UnavailableProduct.unknown(productId));
            } else if (!snapshot.active()) {
                unavailable.add(
                        ProductUnavailableException.UnavailableProduct.discontinued(productId));
            }
        }
        if (!unavailable.isEmpty()) {
            throw new ProductUnavailableException(unavailable);
        }
    }

    private Order buildOrder(CreateOrderRequest request, Map<String, Integer> quantities,
                             Map<String, ProductSnapshot> products, UUID customerId, String actor) {
        Instant now = clock.instant();

        Order order = Order.builder()
                .orderNumber(orderNumberGenerator.next())
                .customerId(customerId)
                .status(OrderStatus.CREATED)
                .deliveryAddress(orderMapper.toDeliveryAddress(request.deliveryAddress()))
                .build();

        quantities.forEach((productId, quantity) -> {
            ProductSnapshot snapshot = products.get(productId);
            // The catalogue values are copied, not referenced: a later rename or repricing must
            // never rewrite what this customer agreed to.
            order.addLine(OrderLine.builder()
                    .productId(snapshot.id())
                    .productSku(snapshot.sku())
                    .productName(snapshot.name())
                    .unitPrice(Money.of(snapshot.price(), snapshot.currency()))
                    .quantity(quantity)
                    .build());
        });

        order.recomputeTotal();
        order.recordCreation(actor, now);
        return order;
    }

    private List<RequestedLine> toRequestedLines(Order order) {
        return order.getLines().stream()
                .map(line -> new RequestedLine(line.getId(), line.getProductId(), line.getQuantity()))
                .toList();
    }

    private ReservationCommand toReservationCommand(Order order, AllocationPlan plan) {
        List<ReservationCommand.Segment> segments = new ArrayList<>();
        for (AllocationSegment segment : plan.segments()) {
            List<ReservationCommand.Line> lines = new ArrayList<>();
            for (SegmentLine line : segment.lines()) {
                lines.add(new ReservationCommand.Line(line.productId(), line.quantity()));
            }
            segments.add(new ReservationCommand.Segment(segment.warehouseId(), lines));
        }
        // The order number is the idempotency key: a retried reservation returns the existing hold
        // instead of taking a second one.
        return new ReservationCommand(order.getOrderNumber(),
                allocationProperties.reservationTtlSeconds(), segments);
    }

    private Order requireVisible(UUID id, UUID callerId, boolean privileged) {
        Order order = orderRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Commande", id));
        assertVisible(order, callerId, privileged);
        return order;
    }

    private void assertVisible(Order order, UUID callerId, boolean privileged) {
        if (!privileged && !order.isOwnedBy(callerId)) {
            throw new AccessDeniedException("This order belongs to another customer.");
        }
    }
}
