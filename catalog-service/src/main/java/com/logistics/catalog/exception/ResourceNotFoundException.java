package com.logistics.catalog.exception;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resource, Object identifier) {
        super(resource + " '" + identifier + "' est introuvable.");
    }
}
