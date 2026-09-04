package com.logistics.inventory.exception;

import java.net.URI;

/** Stable {@code type} URIs for RFC 7807 responses. Clients branch on these, not on wording. */
public final class ProblemTypes {

    private static final String BASE = "https://logistics.local/problems/";

    public static final URI VALIDATION_FAILED = URI.create(BASE + "validation-failed");
    public static final URI MALFORMED_REQUEST = URI.create(BASE + "malformed-request");
    public static final URI RESOURCE_NOT_FOUND = URI.create(BASE + "resource-not-found");
    public static final URI DUPLICATE_RESOURCE = URI.create(BASE + "duplicate-resource");
    public static final URI INSUFFICIENT_STOCK = URI.create(BASE + "insufficient-stock");
    public static final URI INVALID_RESERVATION_STATE = URI.create(BASE + "invalid-reservation-state");
    public static final URI CONFLICT = URI.create(BASE + "conflict");
    public static final URI ACCESS_DENIED = URI.create(BASE + "access-denied");
    public static final URI INVALID_TOKEN = URI.create(BASE + "invalid-token");
    public static final URI INTERNAL_ERROR = URI.create(BASE + "internal-error");

    private ProblemTypes() {
    }
}
