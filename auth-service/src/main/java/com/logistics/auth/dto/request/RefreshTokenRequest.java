package com.logistics.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

/** Carries an opaque refresh token, used by both the refresh and the logout endpoints. */
public record RefreshTokenRequest(

        @NotBlank(message = "refreshToken est obligatoire")
        String refreshToken) {
}
