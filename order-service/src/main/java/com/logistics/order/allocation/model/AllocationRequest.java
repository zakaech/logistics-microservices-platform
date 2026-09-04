package com.logistics.order.allocation.model;

import com.logistics.order.domain.vo.GeoPoint;

import java.util.List;

/**
 * Everything the engine needs, and nothing else.
 *
 * <p>No repository, no HTTP client, no clock. The caller has already fetched the availability
 * snapshot; from here the decision is a pure function of this input, which is what makes every
 * scenario a plain unit test with no mock and no container.
 */
public record AllocationRequest(
        List<RequestedLine> lines,
        GeoPoint destination,
        List<WarehouseCandidate> candidates) {

    public AllocationRequest {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("An allocation request needs at least one line.");
        }
        if (destination == null) {
            throw new IllegalArgumentException("An allocation request needs a destination.");
        }
        lines = List.copyOf(lines);
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }

    /** Total units requested, across every line. */
    public long totalUnits() {
        return lines.stream().mapToLong(RequestedLine::quantity).sum();
    }
}
