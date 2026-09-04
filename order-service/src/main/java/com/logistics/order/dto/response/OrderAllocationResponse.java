package com.logistics.order.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * One shipment.
 *
 * @param distanceKm the distance the decision used, surfaced so the choice is explainable rather
 *                   than merely stated
 */
public record OrderAllocationResponse(
        UUID warehouseId,
        String warehouseCode,
        int shipmentSequence,
        BigDecimal distanceKm,
        List<AllocationLineResponse> lines) {

    public record AllocationLineResponse(UUID orderLineId, String productId, int quantity) {
    }
}
