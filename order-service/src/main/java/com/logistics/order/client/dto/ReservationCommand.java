package com.logistics.order.client.dto;

import java.util.List;
import java.util.UUID;

/**
 * A request to hold stock.
 *
 * @param reference the order number, which inventory-service treats as an idempotency key: a
 *                  retried command returns the existing hold rather than taking a second one
 */
public record ReservationCommand(String reference, int ttlSeconds, List<Segment> segments) {

    public record Segment(UUID warehouseId, List<Line> lines) {
    }

    public record Line(String productId, int quantity) {
    }
}
