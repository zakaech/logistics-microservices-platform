package com.logistics.order.allocation.model;

import com.logistics.order.domain.vo.GeoPoint;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A warehouse the engine may choose, with what it can actually give.
 *
 * @param availableByProduct available units per product id, already net of what is reserved.
 *                           A product absent from the map is simply not stocked here.
 */
public record WarehouseCandidate(
        UUID warehouseId,
        String warehouseCode,
        GeoPoint location,
        Map<String, Integer> availableByProduct) {

    public WarehouseCandidate {
        availableByProduct = availableByProduct == null ? Map.of() : Map.copyOf(availableByProduct);
    }

    public int available(String productId) {
        return availableByProduct.getOrDefault(productId, 0);
    }

    /** True when this warehouse alone can satisfy every line in full - a single shipment. */
    public boolean canCoverFully(List<RequestedLine> lines) {
        return lines.stream().allMatch(line -> available(line.productId()) >= line.quantity());
    }

    /**
     * How many of the requested units this warehouse could contribute.
     *
     * <p>The measure the single-shipment strategy maximises: taking the warehouse that covers the
     * most leaves the fewest units for the next one, and so the fewest shipments overall.
     */
    public long coverage(List<RequestedLine> lines) {
        return lines.stream()
                .mapToLong(line -> Math.min(line.quantity(), available(line.productId())))
                .sum();
    }

    /** True when this warehouse can contribute at least one unit to at least one line. */
    public boolean contributesTo(List<RequestedLine> lines) {
        return coverage(lines) > 0;
    }
}
