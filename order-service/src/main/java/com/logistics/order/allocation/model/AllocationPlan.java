package com.logistics.order.allocation.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The engine's decision: which warehouses ship what.
 *
 * @param strategyName name of the strategy that produced this plan, persisted on the order so a
 *                     decision taken months ago can still be explained
 */
public record AllocationPlan(
        String strategyName,
        boolean splitShipment,
        List<AllocationSegment> segments) {

    public AllocationPlan {
        segments = List.copyOf(segments);
    }

    public static AllocationPlan of(String strategyName, List<AllocationSegment> segments) {
        return new AllocationPlan(strategyName, segments.size() > 1, segments);
    }

    public int shipmentCount() {
        return segments.size();
    }

    public long totalUnits() {
        return segments.stream().mapToLong(AllocationSegment::unitCount).sum();
    }

    /** Distance of the farthest shipment - what the customer actually waits for. */
    public double maxDistanceKm() {
        return segments.stream().mapToDouble(AllocationSegment::distanceKm).max().orElse(0d);
    }

    /**
     * Post-condition: every requested unit is allocated exactly once.
     *
     * <p>Asserted rather than assumed. A strategy that quietly under-allocates would produce an
     * order that looks fulfilled and ships short - the kind of bug that surfaces as an angry
     * customer rather than a stack trace, so it is worth failing loudly here.
     *
     * @throws IllegalStateException if any line is over- or under-allocated
     */
    public void validateCovers(List<RequestedLine> requested) {
        Map<String, Integer> allocated = new HashMap<>();
        for (AllocationSegment segment : segments) {
            for (SegmentLine line : segment.lines()) {
                allocated.merge(line.productId(), line.quantity(), Integer::sum);
            }
        }

        Map<String, Integer> required = new HashMap<>();
        for (RequestedLine line : requested) {
            required.merge(line.productId(), line.quantity(), Integer::sum);
        }

        for (Map.Entry<String, Integer> entry : required.entrySet()) {
            int got = allocated.getOrDefault(entry.getKey(), 0);
            if (got != entry.getValue()) {
                throw new IllegalStateException("Allocation plan produced by '" + strategyName
                        + "' allocates " + got + " of product " + entry.getKey()
                        + " but " + entry.getValue() + " were requested.");
            }
        }
        for (String productId : allocated.keySet()) {
            if (!required.containsKey(productId)) {
                throw new IllegalStateException("Allocation plan produced by '" + strategyName
                        + "' allocates product " + productId + ", which was never requested.");
            }
        }
    }
}
