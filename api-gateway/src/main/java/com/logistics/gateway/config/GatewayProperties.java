package com.logistics.gateway.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * Downstream addresses and edge policy.
 *
 * <p>Service URIs are configuration, not code: the same jar runs against localhost during
 * development and against Docker DNS names in a compose stack.
 *
 * @param services       where each downstream service lives
 * @param jwkSetUri      where the public signing keys are published by auth-service
 * @param issuer         expected value of the {@code iss} claim
 * @param allowedOrigins browser origins allowed by CORS
 */
@Validated
@ConfigurationProperties(prefix = "gateway")
public record GatewayProperties(
        Services services,
        @NotBlank String jwkSetUri,
        @NotBlank String issuer,
        List<String> allowedOrigins) {

    public GatewayProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }

    public record Services(
            @NotBlank String auth,
            @NotBlank String catalog,
            @NotBlank String inventory,
            @NotBlank String order) {
    }
}
