package com.logistics.catalog.domain.enums;

/**
 * Lifecycle of a product.
 *
 * <p>There is no hard delete: an order placed months ago still references the product id, and a
 * dangling reference would make that order unreadable. Removal is therefore a transition to
 * {@link #DISCONTINUED}, which hides the product from the catalogue while keeping it resolvable.
 */
public enum ProductStatus {
    ACTIVE,
    DISCONTINUED
}
