package com.logistics.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Client-credentials payload used by a technical account (decision D9).
 *
 * <p>This is how {@code order-service} obtains a {@code ROLE_SERVICE} token to call the internal
 * endpoints of {@code inventory-service} and {@code catalog-service}, instead of replaying the end
 * user's token.
 */
public record ServiceTokenRequest(

        @NotBlank(message = "clientId est obligatoire")
        String clientId,

        @NotBlank(message = "clientSecret est obligatoire")
        String clientSecret) {
}
