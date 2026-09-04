package com.logistics.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Result of token introspection.
 *
 * <p>An invalid token yields {@code 200} with {@code valid: false} rather than {@code 401}: the
 * question "is this token valid?" was answered successfully. Returning 401 would conflate the
 * caller's own authentication with the subject of the query.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TokenValidationResponse(
        boolean valid,
        String subject,
        String email,
        List<String> roles,
        Instant expiresAt,
        String error) {

    public static TokenValidationResponse valid(String subject, String email,
                                                List<String> roles, Instant expiresAt) {
        return new TokenValidationResponse(true, subject, email, roles, expiresAt, null);
    }

    public static TokenValidationResponse invalid(String reason) {
        return new TokenValidationResponse(false, null, null, null, null, reason);
    }
}
