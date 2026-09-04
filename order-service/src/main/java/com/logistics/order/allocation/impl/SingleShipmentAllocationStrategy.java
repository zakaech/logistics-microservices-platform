package com.logistics.order.allocation.impl;

import com.logistics.order.allocation.AbstractAllocationStrategy;
import com.logistics.order.allocation.distance.DistanceCalculator;
import com.logistics.order.allocation.model.AllocationRequest;
import com.logistics.order.allocation.model.AllocationSegment;
import com.logistics.order.allocation.model.RequestedLine;
import com.logistics.order.allocation.model.SegmentLine;
import com.logistics.order.allocation.model.WarehouseCandidate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Tries hardest to ship the whole order from one warehouse.
 *
 * <p>Optimises the number of parcels rather than the kilometres they travel. One shipment means one
 * picking run, one packing slip, one carrier handover and one delivery for the customer to wait in
 * for - which is usually worth more than a few kilometres of transport.
 *
 * <p>Two phases:
 * <ol>
 *   <li><b>Single shipment.</b> If any warehouse can cover every line on its own, use it - the
 *       closest such site. This is the case the strategy exists for.
 *   <li><b>Fewest shipments.</b> Otherwise, greedily take the warehouse that covers the most of
 *       what is left, and repeat. That is the classic greedy heuristic for set cover: not provably
 *       optimal, but the exact problem is NP-hard, and an exhaustive search over warehouse subsets
 *       is not something to run on an order-placement request.
 * </ol>
 *
 * <p>Distance never disappears - it breaks every tie - but it never outranks coverage either. That
 * is precisely how this strategy differs from {@link NearestWarehouseAllocationStrategy}, and why
 * the two produce different plans on the same input.
 */
@Component
public class SingleShipmentAllocationStrategy extends AbstractAllocationStrategy {

    public static final String NAME = "single-shipment";

    public SingleShipmentAllocationStrategy(DistanceCalculator distanceCalculator) {
        super(distanceCalculator);
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "Prefers a single warehouse able to ship the whole order; otherwise uses as few "
                + "warehouses as possible. Minimises shipments, not distance.";
    }

    @Override
    protected List<AllocationSegment> buildSegments(AllocationRequest request,
                                                    List<WarehouseCandidate> eligible) {
        Optional<WarehouseCandidate> soleFulfiller = eligible.stream()
                .filter(candidate -> candidate.canCoverFully(request.lines()))
                .min(byDistanceThenCode(request));

        if (soleFulfiller.isPresent()) {
            WarehouseCandidate candidate = soleFulfiller.get();
            Remaining remaining = new Remaining(request.lines());
            return List.of(new AllocationSegment(candidate.warehouseId(), candidate.warehouseCode(),
                    distanceTo(candidate, request), 1, remaining.takeFrom(candidate)));
        }

        return buildFewestShipments(request, eligible);
    }

    /**
     * Greedy set cover: at each step take the warehouse contributing the most remaining units.
     *
     * <p>Coverage is recomputed against what is <em>still</em> outstanding, not against the original
     * order. A static ranking would keep favouring a warehouse holding a product that has already
     * been fully allocated by an earlier pick.
     */
    private List<AllocationSegment> buildFewestShipments(AllocationRequest request,
                                                         List<WarehouseCandidate> eligible) {
        Remaining remaining = new Remaining(request.lines());
        List<WarehouseCandidate> pool = new ArrayList<>(eligible);
        List<AllocationSegment> segments = new ArrayList<>();
        int sequence = 1;

        while (!remaining.isEmpty()) {
            List<RequestedLine> outstanding = remaining.asLines();

            Optional<WarehouseCandidate> best = pool.stream()
                    .filter(candidate -> candidate.coverage(outstanding) > 0)
                    .max(Comparator
                            .comparingLong((WarehouseCandidate candidate) -> candidate.coverage(outstanding))
                            // Among equally useful sites, the closest wins; the code makes it
                            // deterministic when even the distance ties.
                            .thenComparing(Comparator.comparingDouble(
                                    (WarehouseCandidate candidate) -> distanceTo(candidate, request)).reversed())
                            .thenComparing(Comparator.comparing(
                                    WarehouseCandidate::warehouseCode).reversed()));

            // Unreachable: the base class proved the network holds enough before calling us. The
            // guard exists so a future change to that contract fails loudly instead of looping.
            if (best.isEmpty()) {
                throw new IllegalStateException(
                        "No warehouse can cover the remaining lines, despite a feasible request.");
            }

            WarehouseCandidate candidate = best.get();
            pool.remove(candidate);

            List<SegmentLine> taken = remaining.takeFrom(candidate);
            segments.add(new AllocationSegment(candidate.warehouseId(), candidate.warehouseCode(),
                    distanceTo(candidate, request), sequence++, taken));
        }

        return segments;
    }

    private Comparator<WarehouseCandidate> byDistanceThenCode(AllocationRequest request) {
        return Comparator
                .comparingDouble((WarehouseCandidate candidate) -> distanceTo(candidate, request))
                .thenComparing(WarehouseCandidate::warehouseCode);
    }
}
