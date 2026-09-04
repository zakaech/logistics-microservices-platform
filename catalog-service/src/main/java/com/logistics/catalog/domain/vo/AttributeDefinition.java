package com.logistics.catalog.domain.vo;

import com.logistics.catalog.domain.enums.AttributeType;

/**
 * One entry of a category's attribute schema.
 *
 * <p>This is the pivot of the whole polyglot-persistence argument: the set of technical attributes a
 * product must carry is <em>data</em>, held by its category, not a column list fixed at deployment
 * time. Adding a category with new attributes needs no migration.
 *
 * @param key      attribute name as it appears in {@code Product.attributes}
 * @param label    human-readable label, for the front-end
 * @param type     how the raw string value is interpreted and validated
 * @param required whether a product of this category must provide it
 */
public record AttributeDefinition(
        String key,
        String label,
        AttributeType type,
        boolean required) {
}
