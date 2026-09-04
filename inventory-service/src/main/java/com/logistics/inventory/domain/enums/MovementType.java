package com.logistics.inventory.domain.enums;

/**
 * Every reason stock changes.
 *
 * <p>The two quantities move independently: {@link #INBOUND}, {@link #OUTBOUND} and
 * {@link #ADJUSTMENT} change what is physically on hand, while {@link #RESERVATION} and
 * {@link #RELEASE} only change what is promised. Recording both kinds in one ledger is what lets a
 * discrepancy be replayed and explained rather than merely noticed.
 */
public enum MovementType {

    /** Goods received. Increases quantity_on_hand. */
    INBOUND,

    /** Goods shipped. Decreases quantity_on_hand. */
    OUTBOUND,

    /** Stock count correction, in either direction. */
    ADJUSTMENT,

    /** Stock promised to an order. Increases quantity_reserved. */
    RESERVATION,

    /** A reservation given back, cancelled or expired. Decreases quantity_reserved. */
    RELEASE
}
