package com.logistics.inventory.exception;

import java.util.List;
import java.util.UUID;

/**
 * Raised when a reservation cannot be satisfied.
 *
 * <p>Carries exactly what is short, per warehouse and product. That detail is needed: when
 * order-service gets this back it re-reads availability and retries the allocation once, and it can
 * only decide sensibly if it knows which line failed and by how much.
 */
public class InsufficientStockException extends RuntimeException {

    private final transient List<Shortage> shortages;

    public InsufficientStockException(List<Shortage> shortages) {
        super("Stock insuffisant pour " + shortages.size() + " ligne(s).");
        this.shortages = List.copyOf(shortages);
    }

    public List<Shortage> getShortages() {
        return shortages;
    }

    /**
     * @param warehouseId where the shortage is
     * @param productId   which product is short
     * @param requested   how much the reservation asked for
     * @param available   how much could actually be held at that moment
     */
    public record Shortage(UUID warehouseId, String productId, int requested, int available) {
    }
}
