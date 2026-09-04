package com.logistics.inventory.controller;

import com.logistics.inventory.dto.request.AvailabilityRequest;
import com.logistics.inventory.dto.request.CreateReservationRequest;
import com.logistics.inventory.dto.response.AvailabilityResponse;
import com.logistics.inventory.dto.response.ReservationResponse;
import com.logistics.inventory.service.ReservationService;
import com.logistics.inventory.service.StockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.UUID;

/**
 * The saga API: availability, then reserve, then confirm or cancel.
 *
 * <p>These endpoints are for order-service, not for browsers, which is why they require
 * {@code ROLE_SERVICE}. That is the point of the technical account (decision D9): even a valid
 * customer token reaching the gateway cannot reserve stock directly.
 */
@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
@Tag(name = "Inventory", description = "Availability snapshot and the reservation protocol")
public class InventoryController {

    private final StockService stockService;
    private final ReservationService reservationService;

    @PostMapping("/availability")
    @PreAuthorize("hasAnyRole('SERVICE', 'WAREHOUSE_MANAGER', 'ADMIN')")
    @Operation(summary = "Stock and warehouse coordinates for a set of products, in one call")
    public AvailabilityResponse availability(@Valid @RequestBody AvailabilityRequest request) {
        return stockService.availability(request);
    }

    /**
     * Holds stock.
     *
     * <p>Answers 201 when the hold was created and 200 when an existing reservation with the same
     * reference was returned. The distinction matters to a caller retrying after a timeout: it
     * tells it whether its first attempt actually landed.
     */
    @PostMapping("/reservations")
    @PreAuthorize("hasRole('SERVICE')")
    @Operation(summary = "Reserve stock; idempotent on the reference")
    public ResponseEntity<ReservationResponse> reserve(
            @Valid @RequestBody CreateReservationRequest request) {
        ReservationService.ReservationResult result = reservationService.reserve(request);

        if (!result.created()) {
            return ResponseEntity.ok(result.response());
        }
        return ResponseEntity
                .created(UriComponentsBuilder.fromPath("/api/v1/inventory/reservations/{id}")
                        .buildAndExpand(result.response().id()).toUri())
                .body(result.response());
    }

    @GetMapping("/reservations/{id}")
    @PreAuthorize("hasAnyRole('SERVICE', 'ADMIN')")
    @Operation(summary = "Fetch a reservation")
    public ReservationResponse getById(@PathVariable UUID id) {
        return reservationService.findById(id);
    }

    @GetMapping("/reservations")
    @PreAuthorize("hasAnyRole('SERVICE', 'ADMIN')")
    @Operation(summary = "Fetch a reservation by order number")
    public ReservationResponse getByReference(@RequestParam String reference) {
        return reservationService.findByReference(reference);
    }

    @PostMapping("/reservations/{id}/confirm")
    @Operation(summary = "Ship the held units: on-hand drops and an OUTBOUND movement is recorded")
    @PreAuthorize("hasRole('SERVICE')")
    public ReservationResponse confirm(@PathVariable UUID id) {
        return reservationService.confirm(id);
    }

    @PostMapping("/reservations/{id}/cancel")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('SERVICE', 'ADMIN')")
    @Operation(summary = "Compensating action of the saga: give the held units back")
    public ReservationResponse cancel(@PathVariable UUID id) {
        return reservationService.cancel(id);
    }
}
