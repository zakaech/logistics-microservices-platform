package com.logistics.order.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.UUID;

/**
 * What each strategy would do with the same order and the same stock.
 *
 * <p>An infeasible strategy is reported rather than thrown: comparing rules is the point, and "this
 * one cannot serve the order" is itself a result worth seeing next to the others.
 */
public record AllocationPreviewResponse(List<StrategyResult> results) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record StrategyResult(
            String strategy,
            String description,
            boolean feasible,
            Boolean splitShipment,
            Integer shipmentCount,
            Double maxDistanceKm,
            List<Segment> segments,
            String failureReason) {
    }

    public record Segment(
            UUID warehouseId,
            String warehouseCode,
            double distanceKm,
            int shipmentSequence,
            List<Line> lines) {
    }

    public record Line(String productId, int quantity) {
    }
}
