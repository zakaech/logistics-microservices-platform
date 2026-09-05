package com.logistics.inventory.exception;

/** Raised when a unique business key (warehouse code, reservation reference) is already taken. */
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String resource, String field, String value) {
        super(resource + " avec " + field + " '" + value + "' existe déjà.");
    }
}
