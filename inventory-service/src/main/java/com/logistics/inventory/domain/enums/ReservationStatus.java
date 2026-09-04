package com.logistics.inventory.domain.enums;

/**
 * Lifecycle of a reservation.
 *
 * <p>Only {@link #ACTIVE} holds stock. Every other state is terminal, which is what makes the
 * transition rules trivial to check and impossible to get subtly wrong.
 */
public enum ReservationStatus {

    /** Stock is held. The only state from which confirm or cancel is legal. */
    ACTIVE,

    /** Stock has left: on-hand decremented, reservation consumed. */
    CONFIRMED,

    /** Released deliberately, as the compensating action of the order saga. */
    CANCELLED,

    /** Released by the sweeper after the TTL passed. The safety net against a crashed orchestrator. */
    EXPIRED;

    public boolean isTerminal() {
        return this != ACTIVE;
    }
}
