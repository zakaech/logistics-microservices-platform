package com.logistics.order.exception;

import com.logistics.order.domain.enums.OrderStatus;

import java.util.Set;

/**
 * Raised when a transition the state machine forbids is attempted.
 *
 * <p>Answers 409 rather than succeeding quietly. Shipping an order that was cancelled, or
 * cancelling one already delivered, almost always means the caller lost track of the lifecycle -
 * and hiding that turns a visible bug into a warehouse dispatching goods nobody is paying for.
 */
public class IllegalOrderStateException extends RuntimeException {

    private final transient OrderStatus currentStatus;
    private final transient Set<OrderStatus> allowedTargets;

    public IllegalOrderStateException(String orderNumber, OrderStatus current, OrderStatus attempted) {
        super("Order '" + orderNumber + "' is " + current + " and cannot move to " + attempted
                + ". Allowed: " + current.allowedTargets() + ".");
        this.currentStatus = current;
        this.allowedTargets = current.allowedTargets();
    }

    public OrderStatus getCurrentStatus() {
        return currentStatus;
    }

    public Set<OrderStatus> getAllowedTargets() {
        return allowedTargets;
    }
}
