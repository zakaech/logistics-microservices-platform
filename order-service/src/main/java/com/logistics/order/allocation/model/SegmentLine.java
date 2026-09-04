package com.logistics.order.allocation.model;

import java.util.UUID;

/** How much of one order line a given warehouse ships. */
public record SegmentLine(UUID orderLineId, String productId, int quantity) {
}
