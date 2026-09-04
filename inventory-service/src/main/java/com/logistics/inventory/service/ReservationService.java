package com.logistics.inventory.service;

import com.logistics.inventory.dto.request.CreateReservationRequest;
import com.logistics.inventory.dto.response.ReservationResponse;

import java.util.UUID;

/**
 * The two-phase stock protocol that makes the order saga safe.
 *
 * <p>Reserving and confirming are separate steps on purpose. Between reading availability and
 * decrementing stock, another order can take the same units; a single "decrement" call would lose
 * that race silently. Reserving takes the units atomically and gives the orchestrator a
 * compensating action ({@link #cancel}) if a later step fails.
 */
public interface ReservationService {

    /**
     * Holds stock across one or more warehouses.
     *
     * <p>Idempotent on {@code reference}: replaying the same order number returns the existing
     * reservation rather than holding the stock twice.
     *
     * @throws com.logistics.inventory.exception.InsufficientStockException listing every shortage
     */
    ReservationResult reserve(CreateReservationRequest request);

    /** Turns the hold into a shipment: on-hand drops and the ledger records an OUTBOUND movement. */
    ReservationResponse confirm(UUID id);

    /** Compensating action: gives the held units back. */
    ReservationResponse cancel(UUID id);

    ReservationResponse findById(UUID id);

    ReservationResponse findByReference(String reference);

    /**
     * Releases holds whose TTL has passed. The safety net for an orchestrator that crashed between
     * reserving and confirming; without it, stock would stay held forever.
     *
     * @return how many reservations were expired
     */
    int expireOverdueReservations();

    /**
     * @param response the reservation
     * @param created  false when an existing reservation was returned, so the controller can answer
     *                 201 or 200 accordingly
     */
    record ReservationResult(ReservationResponse response, boolean created) {
    }
}
