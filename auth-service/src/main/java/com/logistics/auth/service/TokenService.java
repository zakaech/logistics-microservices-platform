package com.logistics.auth.service;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Issues and validates access tokens.
 *
 * <p>Kept behind an interface with no JOSE type in its signature: the rest of the service talks
 * about subjects, roles and expiry dates, not about JWTs. Replacing the token format would touch one
 * implementation class.
 */
public interface TokenService {

    /**
     * @param subject the value of the {@code sub} claim - a user id, or a client id for a service
     * @param email   the {@code email} claim; null for technical accounts
     * @param roles   authority names, written as-is into the {@code roles} claim
     */
    IssuedToken issueAccessToken(String subject, String email, Set<String> roles);

    /**
     * @throws com.logistics.auth.exception.InvalidTokenException if the signature, the issuer or the
     *                                                            expiry does not check out
     */
    ValidatedToken validate(String rawToken);

    /** Lifetime of an access token in seconds, as advertised in {@code expiresIn}. */
    long accessTokenTtlSeconds();

    record IssuedToken(String value, Instant expiresAt) {
    }

    record ValidatedToken(String subject, String email, List<String> roles, Instant expiresAt) {
    }
}
