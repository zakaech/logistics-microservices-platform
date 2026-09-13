package com.logistics.order.allocation.impl;

import com.logistics.order.allocation.AbstractAllocationStrategy;
import com.logistics.order.allocation.distance.DistanceCalculator;
import com.logistics.order.allocation.model.AllocationRequest;
import com.logistics.order.allocation.model.AllocationSegment;
import com.logistics.order.allocation.model.SegmentLine;
import com.logistics.order.allocation.model.WarehouseCandidate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Ships from the closest warehouses first.
 *
 * <p>Optimises delivery time and transport cost: walk the sites from nearest to farthest, taking
 * whatever each can give, until the order is filled.
 *
 * <p>The trade-off is explicit and is the whole point of having a second strategy to compare it
 * with: this one will split an order across two nearby sites even when a single, slightly
 * more distant warehouse could have shipped everything in one parcel. That is the right answer when
 * transport is billed by distance, and the wrong one when it is billed per shipment.
 */
@Component
public class NearestWarehouseAllocationStrategy extends AbstractAllocationStrategy {

    public static final String NAME = "nearest";

    public NearestWarehouseAllocationStrategy(DistanceCalculator distanceCalculator) {
        super(distanceCalculator);
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "Expédie d'abord depuis les entrepôts les plus proches, en fractionnant la "
                + "commande lorsque le site le plus proche ne peut pas la couvrir. Minimise la "
                + "distance, pas le nombre d'expéditions.";
    }

    @Override
    protected List<AllocationSegment> buildSegments(AllocationRequest request,
                                                    List<WarehouseCandidate> eligible) {
        // Distance decides. Coverage breaks a tie between equidistant sites - preferring the one
        // that gives more keeps the shipment count down at no cost in distance. The warehouse code
        // is the final tie-break, and it is what makes the plan reproducible rather than dependent
        // on the order the availability snapshot happened to arrive in.
        List<WarehouseCandidate> ordered = eligible.stream()
                .sorted(Comparator
                        .comparingDouble((WarehouseCandidate candidate) -> distanceTo(candidate, request))
                        .thenComparing(Comparator.comparingLong(
                                (WarehouseCandidate candidate) -> candidate.coverage(request.lines())).reversed())
                        .thenComparing(WarehouseCandidate::warehouseCode))
                .toList();

        Remaining remaining = new Remaining(request.lines());
        List<AllocationSegment> segments = new ArrayList<>();
        int sequence = 1;

        for (WarehouseCandidate candidate : ordered) {
            if (remaining.isEmpty()) {
                break;
            }
            List<SegmentLine> taken = remaining.takeFrom(candidate);
            if (!taken.isEmpty()) {
                segments.add(new AllocationSegment(candidate.warehouseId(), candidate.warehouseCode(),
                        distanceTo(candidate, request), sequence++, taken));
            }
        }

        return segments;
    }
}
