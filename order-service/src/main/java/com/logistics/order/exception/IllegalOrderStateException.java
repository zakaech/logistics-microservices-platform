package com.logistics.order.exception;

import com.logistics.order.domain.enums.OrderStatus;

import java.util.Set;

/**
 * Raised when a transition the state machine forbids is attempted.
 *
 * <p>Answers 409 rather than succeeding quietly. Shipping an order that was cancelled, or
 * cancelling one already delivered, almost always means the caller lost track of the lifecycle -
 * and hiding that turns a visible bug into goods dispatched for a cancelled order.
 */
public class IllegalOrderStateException extends RuntimeException {

    private final transient OrderStatus currentStatus;
    private final transient Set<OrderStatus> allowedTargets;

    public IllegalOrderStateException(String orderNumber, OrderStatus current, OrderStatus attempted) {
        // Le message emploie les libellés ; les valeurs techniques restent exposées telles
        // quelles dans currentStatus et allowedTargets, que le front-end lit pour son affichage.
        super("La commande '" + orderNumber + "' est " + current.label()
                + " et ne peut pas passer à " + attempted.label() + ". Transitions autorisées : "
                + current.allowedTargets().stream().map(OrderStatus::label).toList() + ".");
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
