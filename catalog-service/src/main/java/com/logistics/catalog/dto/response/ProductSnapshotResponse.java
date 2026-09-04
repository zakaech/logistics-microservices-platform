package com.logistics.catalog.dto.response;

import com.logistics.catalog.domain.enums.ProductStatus;
import com.logistics.catalog.dto.common.MoneyDto;

/**
 * Exactly what an order line needs, and nothing more.
 *
 * <p>order-service copies these values into the order at creation time, so a later rename or
 * repricing does not rewrite history. Keeping this record minimal is what keeps that coupling
 * narrow: the fewer fields cross the boundary, the fewer reasons the two services have to change
 * together.
 */
public record ProductSnapshotResponse(
        String id,
        String sku,
        String name,
        MoneyDto price,
        ProductStatus status) {
}
