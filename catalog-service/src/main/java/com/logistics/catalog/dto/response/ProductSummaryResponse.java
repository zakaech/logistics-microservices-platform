package com.logistics.catalog.dto.response;

import com.logistics.catalog.domain.enums.ProductStatus;
import com.logistics.catalog.dto.common.MoneyDto;

/**
 * Listing view.
 *
 * <p>Deliberately narrower than the full response: a catalogue page shows dozens of products, and
 * shipping every technical sheet and description would multiply the payload for data the grid never
 * displays.
 */
public record ProductSummaryResponse(
        String id,
        String sku,
        String name,
        String brand,
        String categoryId,
        String categoryPath,
        MoneyDto price,
        String primaryImage,
        ProductStatus status) {
}
