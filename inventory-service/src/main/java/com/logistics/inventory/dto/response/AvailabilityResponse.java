package com.logistics.inventory.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Stock and geography in one payload.
 *
 * <p>The coordinates travel with the availability because the allocation engine needs both to rank
 * candidates. Splitting them across two endpoints would turn one call into one-per-warehouse - the
 * N+1 problem, over HTTP.
 */
public record AvailabilityResponse(List<WarehouseAvailability> warehouses) {

    /**
     * @param availability available quantity per product id, already net of what is reserved.
     *                     A product absent from the map is not stocked at that site.
     */
    public record WarehouseAvailability(
            UUID warehouseId,
            String code,
            BigDecimal latitude,
            BigDecimal longitude,
            Map<String, Integer> availability) {
    }
}
