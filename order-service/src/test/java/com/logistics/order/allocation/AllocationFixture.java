package com.logistics.order.allocation;

import com.logistics.order.allocation.model.AllocationRequest;
import com.logistics.order.allocation.model.AllocationSegment;
import com.logistics.order.allocation.model.RequestedLine;
import com.logistics.order.allocation.model.WarehouseCandidate;
import com.logistics.order.domain.vo.GeoPoint;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Readable test data.
 *
 * <p>Coordinates are chosen so distances are obvious by inspection: the destination sits at 0,0 and
 * each warehouse is placed a whole number of degrees north of it, so "one degree away" reads as
 * "closer than two degrees away" without anyone having to compute a haversine in their head.
 */
final class AllocationFixture {

    static final String P1 = "product-1";
    static final String P2 = "product-2";

    /** Delivery address for every scenario. */
    static final GeoPoint DESTINATION = GeoPoint.of(0.0, 0.0);

    private AllocationFixture() {
    }

    /** A warehouse `degreesNorth` degrees from the destination, holding the given stock. */
    static WarehouseCandidate warehouse(String code, double degreesNorth, Object... productAndQuantity) {
        Map<String, Integer> stock = new LinkedHashMap<>();
        for (int i = 0; i < productAndQuantity.length; i += 2) {
            stock.put((String) productAndQuantity[i], (Integer) productAndQuantity[i + 1]);
        }
        return new WarehouseCandidate(UUID.randomUUID(), code, GeoPoint.of(degreesNorth, 0.0), stock);
    }

    static RequestedLine line(String productId, int quantity) {
        return RequestedLine.of(productId, quantity);
    }

    static AllocationRequest request(List<RequestedLine> lines, WarehouseCandidate... candidates) {
        return new AllocationRequest(lines, DESTINATION, List.of(candidates));
    }

    /** Units of one product shipped by one segment, or zero if that product is not in it. */
    static int unitsOf(AllocationSegment segment, String productId) {
        return segment.lines().stream()
                .filter(line -> line.productId().equals(productId))
                .mapToInt(line -> line.quantity())
                .sum();
    }
}
