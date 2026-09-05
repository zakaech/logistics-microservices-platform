package com.logistics.order.exception;

import java.util.List;

/**
 * Raised when no combination of warehouses can fulfil an order.
 *
 * <p>Carries what is short across the whole network, not just the first failure: a customer told
 * "unavailable" learns nothing, whereas "we hold 6 of the 10 you asked for" is actionable, and the
 * back-office can see immediately whether this is a stock problem or a coverage problem.
 */
public class AllocationFailedException extends RuntimeException {

    private final transient List<UnsatisfiedLine> unsatisfied;

    public AllocationFailedException(List<UnsatisfiedLine> unsatisfied) {
        super("Aucune combinaison d'entrepôts ne peut satisfaire cette commande.");
        this.unsatisfied = List.copyOf(unsatisfied);
    }

    public List<UnsatisfiedLine> getUnsatisfied() {
        return unsatisfied;
    }

    /**
     * @param availableAcrossNetwork total units available in every active warehouse combined, which
     *                               is what makes the difference between "out of stock" and
     *                               "in stock but not reachable in one plan" visible
     */
    public record UnsatisfiedLine(String productId, int requested, long availableAcrossNetwork) {
    }
}
