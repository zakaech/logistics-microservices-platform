package com.logistics.auth.exception;

/** Raised when an entity addressed by id does not exist. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resource, Object identifier) {
        super(resource + " '" + identifier + "' est introuvable.");
    }
}
