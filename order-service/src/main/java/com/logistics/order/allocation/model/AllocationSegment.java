package com.logistics.order.allocation.model;

import java.util.List;
import java.util.UUID;

/**
 * One shipment: what a single warehouse contributes.
 *
 * @param distanceKm       distance used by the decision, kept so a past choice can be explained
 * @param shipmentSequence 1-based order of the shipments in the plan
 */
public record AllocationSegment(
        UUID warehouseId,
        String warehouseCode,
        double distanceKm,
        int shipmentSequence,
        List<SegmentLine> lines) {

    public AllocationSegment {
        lines = List.copyOf(lines);
    }

    public long unitCount() {
        return lines.stream().mapToLong(SegmentLine::quantity).sum();
    }
}
