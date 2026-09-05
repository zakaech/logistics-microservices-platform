package com.logistics.order.controller;

import com.logistics.order.domain.enums.OrderStatus;
import com.logistics.order.dto.request.CancelOrderRequest;
import com.logistics.order.dto.request.CreateOrderRequest;
import com.logistics.order.dto.request.UpdateOrderStatusRequest;
import com.logistics.order.dto.response.OrderAllocationResponse;
import com.logistics.order.dto.response.OrderResponse;
import com.logistics.order.dto.response.OrderStatusHistoryResponse;
import com.logistics.order.dto.response.OrderSummaryResponse;
import com.logistics.order.dto.response.PagedResponse;
import com.logistics.order.security.OrderCaller;
import com.logistics.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Order endpoints.
 *
 * <p>A customer sees only their own orders; staff see all. That distinction is decided here from
 * the caller's roles and enforced in the service, so no controller can forget it by accident.
 */
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Création, suivi et cycle de vie des commandes")
public class OrderController {

    private static final int MAX_PAGE_SIZE = 100;

    private final OrderService orderService;
    private final OrderCaller caller;

    @PostMapping
    @PreAuthorize("hasAnyRole('CLIENT', 'ADMIN')")
    @Operation(summary = "Passer une commande : résolution catalogue, valorisation, affectation, réservation et confirmation")
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request) {
        // The customer is the token subject, never a field in the body.
        OrderResponse created = orderService.create(request, caller.customerId(), caller.actor());
        return ResponseEntity
                .created(UriComponentsBuilder.fromPath("/api/v1/orders/{id}")
                        .buildAndExpand(created.id()).toUri())
                .body(created);
    }

    @GetMapping
    @Operation(summary = "Lister les commandes ; un client ne voit que les siennes")
    public PagedResponse<OrderSummaryResponse> list(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        // Staff get an unfiltered view; everyone else is scoped to their own id.
        UUID scope = caller.isPrivileged() ? null : caller.customerId();
        return orderService.search(scope, status, from, to, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulter une commande, ses lignes et ses expéditions")
    public OrderResponse getById(@PathVariable UUID id) {
        return orderService.findById(id, callerIdOrNull(), caller.isPrivileged());
    }

    @GetMapping("/{id}/allocations")
    @Operation(summary = "Les expéditions décidées par le moteur, avec la distance utilisée")
    public List<OrderAllocationResponse> allocations(@PathVariable UUID id) {
        return orderService.findAllocations(id, callerIdOrNull(), caller.isPrivileged());
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "Historique de la commande : chaque transition et son motif")
    public List<OrderStatusHistoryResponse> history(@PathVariable UUID id) {
        return orderService.findHistory(id, callerIdOrNull(), caller.isPrivileged());
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Annuler une commande et libérer le stock qu'elle retient encore")
    public OrderResponse cancel(@PathVariable UUID id,
                                @Valid @RequestBody(required = false) CancelOrderRequest request) {
        return orderService.cancel(id, request, callerIdOrNull(), caller.isPrivileged(),
                caller.actor());
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('WAREHOUSE_MANAGER', 'ADMIN')")
    @Operation(summary = "Avancement logistique : SHIPPED, puis DELIVERED")
    public OrderResponse changeStatus(@PathVariable UUID id,
                                      @Valid @RequestBody UpdateOrderStatusRequest request) {
        return orderService.changeStatus(id, request.status(), request.reason(), caller.actor());
    }

    /** Staff are not scoped to a customer id, and a service token has none to give. */
    private UUID callerIdOrNull() {
        return caller.isPrivileged() ? null : caller.customerId();
    }
}
