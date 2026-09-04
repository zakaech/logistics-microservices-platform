package com.logistics.order.allocation.model;

import java.util.UUID;

/**
 * One line the allocation must satisfy.
 *
 * @param orderLineId identifies the line so a split can be attributed back to it; null when the
 *                    engine runs in preview mode, before any order exists
 * @param productId   logical reference to the catalogue
 * @param quantity    units required, always positive
 */
public record RequestedLine(UUID orderLineId, String productId, int quantity) {

    public RequestedLine {
        if (productId == null || productId.isBlank()) {
            throw new IllegalArgumentException("A requested line needs a productId.");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("A requested line needs a positive quantity.");
        }
    }

    public static RequestedLine of(String productId, int quantity) {
        return new RequestedLine(null, productId, quantity);
    }
}
