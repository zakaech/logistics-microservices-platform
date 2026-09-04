package com.logistics.inventory.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Where to fetch the public signing keys, and which issuer to trust.
 *
 * <p>This service holds no key material of its own: it verifies, it never signs.
 */
@Validated
@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties(
        @NotBlank String jwkSetUri,
        @NotBlank String issuer) {
}
