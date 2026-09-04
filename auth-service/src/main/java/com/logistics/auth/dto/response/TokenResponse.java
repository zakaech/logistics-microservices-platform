package com.logistics.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Issued credentials.
 *
 * @param accessToken  signed JWT, short-lived, sent on every call as a Bearer token
 * @param refreshToken opaque token, long-lived; absent for service tokens, which are re-requested
 *                     with client credentials instead
 * @param tokenType    always {@code Bearer}
 * @param expiresIn    lifetime of the access token in seconds
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn) {

    private static final String BEARER = "Bearer";

    public static TokenResponse of(String accessToken, String refreshToken, long expiresIn) {
        return new TokenResponse(accessToken, refreshToken, BEARER, expiresIn);
    }

    public static TokenResponse ofAccessTokenOnly(String accessToken, long expiresIn) {
        return new TokenResponse(accessToken, null, BEARER, expiresIn);
    }
}
