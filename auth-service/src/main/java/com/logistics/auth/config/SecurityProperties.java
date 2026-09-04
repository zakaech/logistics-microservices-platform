package com.logistics.auth.config;

import com.logistics.auth.domain.enums.RoleName;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

/**
 * Every security setting of the service, bound from configuration.
 *
 * <p>A single record covers the whole {@code security.*} namespace: two
 * {@code @ConfigurationProperties} classes with overlapping prefixes would bind ambiguously.
 * No value has a hard-coded secret as a default - secrets come from the environment or the service
 * refuses to start.
 *
 * @param jwt            token signing and lifetime settings
 * @param serviceClients technical accounts allowed to request a token via client credentials,
 *                       keyed by client id
 * @param bootstrapAdmin first administrator, created at startup when absent
 */
@Validated
@ConfigurationProperties(prefix = "security")
public record SecurityProperties(
        @NotNull @Valid Jwt jwt,
        Map<String, @Valid ServiceClient> serviceClients,
        @Valid BootstrapAdmin bootstrapAdmin) {

    public SecurityProperties {
        serviceClients = serviceClients == null ? Map.of() : Map.copyOf(serviceClients);
    }

    /**
     * @param issuer          value of the {@code iss} claim, verified by every resource server
     * @param accessTokenTtl  lifetime of an access token (ISO-8601 duration, e.g. {@code PT15M})
     * @param refreshTokenTtl lifetime of a refresh token (e.g. {@code P7D})
     * @param keyId           {@code kid} advertised in the JWKS and in every token header
     * @param privateKey      PKCS#8 PEM, raw or base64-encoded; empty means "generate an ephemeral pair"
     * @param publicKey       X.509 PEM, raw or base64-encoded; empty means "generate an ephemeral pair"
     */
    public record Jwt(
            @NotBlank String issuer,
            @NotNull Duration accessTokenTtl,
            @NotNull Duration refreshTokenTtl,
            @NotBlank String keyId,
            String privateKey,
            String publicKey) {

        /** True when both keys were supplied; otherwise a throw-away pair is generated at startup. */
        public boolean hasConfiguredKeyPair() {
            return privateKey != null && !privateKey.isBlank()
                    && publicKey != null && !publicKey.isBlank();
        }
    }

    /**
     * @param secret shared secret compared in constant time; the environment is the secret store
     * @param roles  authorities granted to the resulting token, normally {@code ROLE_SERVICE} alone
     */
    public record ServiceClient(String secret, @NotNull Set<RoleName> roles) {

        public boolean isUsable() {
            return secret != null && !secret.isBlank();
        }
    }

    public record BootstrapAdmin(boolean enabled, String email, String password) {

        public boolean isComplete() {
            return enabled
                    && email != null && !email.isBlank()
                    && password != null && !password.isBlank();
        }
    }
}
