package com.logistics.catalog.dto.response;

import com.logistics.catalog.domain.enums.ProductStatus;
import com.logistics.catalog.dto.common.DimensionsDto;
import com.logistics.catalog.dto.common.MoneyDto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Full product view, for the detail screen. */
public record ProductResponse(
        String id,
        String sku,
        String name,
        String description,
        String brand,
        String categoryId,
        String categoryPath,
        MoneyDto price,
        Map<String, String> attributes,
        DimensionsDto dimensions,
        List<String> images,
        ProductStatus status,
        Instant createdAt,
        Instant updatedAt) {
}
