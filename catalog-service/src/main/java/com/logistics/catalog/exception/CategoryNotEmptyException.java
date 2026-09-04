package com.logistics.catalog.exception;

/**
 * Raised when deleting a category that still has children or products.
 *
 * <p>Cascading would silently orphan or delete products; refusing forces the caller to decide.
 */
public class CategoryNotEmptyException extends RuntimeException {

    public CategoryNotEmptyException(String categoryId, String reason) {
        super("Category '" + categoryId + "' cannot be deleted: " + reason + ".");
    }
}
