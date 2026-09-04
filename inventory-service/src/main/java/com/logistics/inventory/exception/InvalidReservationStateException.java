package com.logistics.inventory.exception;

import com.logistics.inventory.domain.enums.ReservationStatus;

/**
 * Raised when confirming or cancelling a reservation that is no longer ACTIVE.
 *
 * <p>Answering 409 rather than silently succeeding matters: a second confirm almost always means
 * the caller lost track of its own saga, and hiding that would turn a visible bug into a stock
 * discrepancy discovered weeks later.
 */
public class InvalidReservationStateException extends RuntimeException {

    private final ReservationStatus currentStatus;

    public InvalidReservationStateException(String reference, ReservationStatus currentStatus,
                                            String attemptedAction) {
        super("Reservation '" + reference + "' is " + currentStatus
                + " and cannot be " + attemptedAction + ".");
        this.currentStatus = currentStatus;
    }

    public ReservationStatus getCurrentStatus() {
        return currentStatus;
    }
}
