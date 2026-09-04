package com.logistics.order.allocation;

import com.logistics.order.allocation.distance.DistanceCalculator;
import com.logistics.order.allocation.model.AllocationPlan;
import com.logistics.order.allocation.model.AllocationRequest;
import com.logistics.order.allocation.model.AllocationSegment;
import com.logistics.order.allocation.model.RequestedLine;
import com.logistics.order.allocation.model.SegmentLine;
import com.logistics.order.allocation.model.WarehouseCandidate;
import com.logistics.order.exception.AllocationFailedException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What every allocation strategy does the same way.
 *
 * <p>Template Method: the skeleton below - filter, prove feasibility, build, verify - is fixed, and
 * subclasses supply only {@link #buildSegments}, the part that actually decides. Three things are
 * therefore impossible to get wrong in a new strategy, because a subclass never touches them: the
 * feasibility check, the post-condition, and the shape of the failure.
 *
 * <p>Feasibility is checked <em>before</em> the strategy runs, on purpose. Whether the network holds
 * enough stock is not a matter of strategy - every strategy would reach the same verdict - so a
 * subclass that forgot to check could otherwise return a quietly incomplete plan.
 */
public abstract class AbstractAllocationStrategy implements WarehouseAllocationStrategy {

    protected final DistanceCalculator distanceCalculator;

    protected AbstractAllocationStrategy(DistanceCalculator distanceCalculator) {
        this.distanceCalculator = distanceCalculator;
    }

    @Override
    public final AllocationPlan allocate(AllocationRequest request) {
        // 1. Only warehouses that can contribute something are candidates. An empty or irrelevant
        //    site would otherwise survive into the ordering and produce empty shipments.
        List<WarehouseCandidate> eligible = request.candidates().stream()
                .filter(candidate -> candidate.contributesTo(request.lines()))
                .toList();

        // 2. Can the network satisfy this at all? Answered once, identically for every strategy.
        assertFeasible(request, eligible);

        // 3. The part that differs.
        List<AllocationSegment> segments = buildSegments(request, eligible);

        AllocationPlan plan = AllocationPlan.of(name(), segments);

        // 4. Never trust the subclass: assert that every requested unit is allocated exactly once.
        plan.validateCovers(request.lines());
        return plan;
    }

    /**
     * Builds the shipments. Called with candidates already filtered and feasibility already proven,
     * so an implementation may assume the request <em>can</em> be satisfied.
     */
    protected abstract List<AllocationSegment> buildSegments(AllocationRequest request,
                                                            List<WarehouseCandidate> eligible);

    // --- shared helpers ----------------------------------------------------

    protected double distanceTo(WarehouseCandidate candidate, AllocationRequest request) {
        return distanceCalculator.distanceKm(candidate.location(), request.destination());
    }

    /**
     * Mutable view of what still has to be shipped, consumed as segments are built.
     *
     * <p>Public because it is part of the contract a strategy author works against: a subclass in
     * another package cannot reach protected members of a nested type it does not itself extend.
     */
    public static final class Remaining {

        private final List<RequestedLine> original;
        private final Map<String, Integer> remainingByProduct = new HashMap<>();
        private final Map<String, UUID> orderLineIdByProduct = new HashMap<>();

        public Remaining(List<RequestedLine> lines) {
            this.original = lines;
            for (RequestedLine line : lines) {
                remainingByProduct.merge(line.productId(), line.quantity(), Integer::sum);
                orderLineIdByProduct.putIfAbsent(line.productId(), line.orderLineId());
            }
        }

        public boolean isEmpty() {
            return remainingByProduct.values().stream().allMatch(quantity -> quantity == 0);
        }

        public List<RequestedLine> asLines() {
            return remainingByProduct.entrySet().stream()
                    .filter(entry -> entry.getValue() > 0)
                    .map(entry -> new RequestedLine(orderLineIdByProduct.get(entry.getKey()),
                            entry.getKey(), entry.getValue()))
                    .toList();
        }

        public List<RequestedLine> originalLines() {
            return original;
        }

        /** Takes as much as this warehouse can give, and returns what it gave. */
        public List<SegmentLine> takeFrom(WarehouseCandidate candidate) {
            List<SegmentLine> taken = new ArrayList<>();
            for (Map.Entry<String, Integer> entry : remainingByProduct.entrySet()) {
                String productId = entry.getKey();
                int stillNeeded = entry.getValue();
                if (stillNeeded == 0) {
                    continue;
                }
                int quantity = Math.min(stillNeeded, candidate.available(productId));
                if (quantity > 0) {
                    taken.add(new SegmentLine(orderLineIdByProduct.get(productId), productId, quantity));
                    entry.setValue(stillNeeded - quantity);
                }
            }
            return taken;
        }
    }

    /**
     * Refuses the order when the whole network, warehouses combined, holds too little.
     *
     * <p>Deliberately measured across every eligible warehouse rather than per site: a customer
     * ordering ten units that exist as six here and four there must be served by a split, not
     * refused.
     */
    private void assertFeasible(AllocationRequest request, List<WarehouseCandidate> eligible) {
        List<AllocationFailedException.UnsatisfiedLine> unsatisfied = new ArrayList<>();

        for (RequestedLine line : request.lines()) {
            long availableAcrossNetwork = eligible.stream()
                    .mapToLong(candidate -> candidate.available(line.productId()))
                    .sum();

            if (availableAcrossNetwork < line.quantity()) {
                unsatisfied.add(new AllocationFailedException.UnsatisfiedLine(
                        line.productId(), line.quantity(), availableAcrossNetwork));
            }
        }

        if (!unsatisfied.isEmpty()) {
            throw new AllocationFailedException(unsatisfied);
        }
    }
}
