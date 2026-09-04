package com.logistics.auth.exception;

import java.net.URI;

/**
 * Stable {@code type} URIs for RFC 7807 responses.
 *
 * <p>They are identifiers, not links to fetch: a client branches on the type rather than on the
 * human-readable {@code detail}, which is free to change wording without breaking anyone.
 */
public final class ProblemTypes {

    private static final String BASE = "https://logistics.local/problems/";

    public static final URI VALIDATION_FAILED = URI.create(BASE + "validation-failed");
    public static final URI MALFORMED_REQUEST = URI.create(BASE + "malformed-request");
    public static final URI INVALID_CREDENTIALS = URI.create(BASE + "invalid-credentials");
    public static final URI INVALID_TOKEN = URI.create(BASE + "invalid-token");
    public static final URI EMAIL_ALREADY_USED = URI.create(BASE + "email-already-used");
    public static final URI RESOURCE_NOT_FOUND = URI.create(BASE + "resource-not-found");
    public static final URI ACCESS_DENIED = URI.create(BASE + "access-denied");
    public static final URI CONFLICT = URI.create(BASE + "conflict");
    public static final URI INTERNAL_ERROR = URI.create(BASE + "internal-error");

    private ProblemTypes() {
        // constant holder
    }
}
