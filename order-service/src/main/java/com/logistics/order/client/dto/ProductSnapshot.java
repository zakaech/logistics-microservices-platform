package com.logistics.order.client.dto;

import java.math.BigDecimal;

/**
 * What catalog-service tells us about a product, translated into our own vocabulary.
 *
 * <p>This is the anti-corruption layer: the JSON shape catalog-service happens to use stops at the
 * client package. If it grows a field or renames one, this record and one adapter change, and the
 * order domain does not.
 */
public record ProductSnapshot(
        String id,
        String sku,
        String name,
        BigDecimal price,
        String currency,
        boolean active) {
}
