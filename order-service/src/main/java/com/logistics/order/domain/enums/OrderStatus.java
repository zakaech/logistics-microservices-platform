package com.logistics.order.domain.enums;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The order lifecycle.
 *
 * <p>The happy path is {@code CREATED -> ALLOCATED -> CONFIRMED -> SHIPPED -> DELIVERED}, with
 * {@code CANCELLED} reachable until the goods leave, and {@code REJECTED} for an order the network
 * cannot serve.
 *
 * <p>{@link #CONFIRMED} sits between allocation and shipping because reserving stock and consuming
 * it are two distinct events in the saga: {@code ALLOCATED} means units are <em>held</em>,
 * {@code CONFIRMED} means they have actually left the shelf. Collapsing the two would leave no
 * state in which a hold exists but is not yet spent - which is precisely the window the
 * compensating action needs.
 *
 * <p>{@link #REJECTED} exists so that a failed allocation is still a record with a reason, rather
 * than a request that vanished. A customer can be told why, and the case can be analysed.
 *
 * <p>The transitions live in a table rather than in a State-pattern class hierarchy. With seven
 * states and no behaviour varying beyond "may I move there?", a table is the smaller, more readable
 * answer - knowing when not to apply a pattern is part of the design.
 */
public enum OrderStatus {

    /** Persisted with its lines and total; no stock held yet. */
    CREATED("Créée"),

    /** The engine produced a plan and inventory-service is holding the units. */
    ALLOCATED("Affectée"),

    /** The hold was consumed: stock has left the warehouse. */
    CONFIRMED("Confirmée"),

    /** Handed to the carrier. */
    SHIPPED("Expédiée"),

    DELIVERED("Livrée"),

    /** Cancelled before shipping; any hold has been released. */
    CANCELLED("Annulée"),

    /** No combination of warehouses could serve it. Terminal, and keeps its reason. */
    REJECTED("Rejetée");

    /**
     * Libellé destiné aux messages lus par un utilisateur.
     *
     * <p>Purely for display. The constant name stays the contract: it is what JSON carries,
     * what the column stores and what the front-end keys its labels on.
     */
    private final String label;

    OrderStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = new EnumMap<>(OrderStatus.class);

    static {
        ALLOWED.put(CREATED, EnumSet.of(ALLOCATED, REJECTED, CANCELLED));
        ALLOWED.put(ALLOCATED, EnumSet.of(CONFIRMED, CANCELLED));
        ALLOWED.put(CONFIRMED, EnumSet.of(SHIPPED, CANCELLED));
        ALLOWED.put(SHIPPED, EnumSet.of(DELIVERED));
        ALLOWED.put(DELIVERED, EnumSet.noneOf(OrderStatus.class));
        ALLOWED.put(CANCELLED, EnumSet.noneOf(OrderStatus.class));
        ALLOWED.put(REJECTED, EnumSet.noneOf(OrderStatus.class));
    }

    public boolean canTransitionTo(OrderStatus target) {
        return ALLOWED.getOrDefault(this, Set.of()).contains(target);
    }

    public Set<OrderStatus> allowedTargets() {
        return Collections.unmodifiableSet(ALLOWED.getOrDefault(this, Set.of()));
    }

    public boolean isTerminal() {
        return allowedTargets().isEmpty();
    }

    /** True while stock is held but not yet consumed - the window the saga can compensate in. */
    public boolean holdsStock() {
        return this == ALLOCATED;
    }

    /** True once the goods have left: cancelling from here needs a physical return, not a release. */
    public boolean stockHasLeft() {
        return this == CONFIRMED || this == SHIPPED || this == DELIVERED;
    }
}
