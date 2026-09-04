package com.logistics.auth.service;

import com.logistics.auth.domain.entity.RefreshToken;
import com.logistics.auth.domain.entity.User;

/**
 * Lifecycle of refresh tokens: issue, verify, rotate, revoke.
 *
 * <p>The raw token value is returned exactly once, when it is created. Only its hash is persisted,
 * so this interface is the single place where a raw value and a stored row meet.
 */
public interface RefreshTokenService {

    /** Creates a token for the user and returns its raw value alongside the persisted row. */
    IssuedRefreshToken issue(User user);

    /**
     * @throws com.logistics.auth.exception.InvalidTokenException if unknown, revoked or expired
     */
    RefreshToken verify(String rawToken);

    /**
     * Revokes the presented token and issues a new one for the same user (rotation).
     *
     * <p>A refresh token is single-use: replaying one that has already been exchanged fails, which
     * turns a stolen token into a detectable event rather than a permanent foothold.
     */
    IssuedRefreshToken rotate(String rawToken);

    void revoke(String rawToken);

    /** Deletes rows whose expiry has passed. Returns how many were removed. */
    int purgeExpired();

    record IssuedRefreshToken(String rawValue, RefreshToken entity) {
    }
}
