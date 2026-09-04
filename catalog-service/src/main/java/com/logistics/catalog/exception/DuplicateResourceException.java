package com.logistics.catalog.exception;

/** Raised when a unique business key (SKU, category slug) is already taken. */
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String resource, String field, String value) {
        super(resource + " with " + field + " '" + value + "' already exists.");
    }
}
