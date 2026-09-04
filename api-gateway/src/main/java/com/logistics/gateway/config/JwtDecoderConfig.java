package com.logistics.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

/**
 * Reactive decoder backed by the JWKS published by auth-service.
 *
 * <p>The gateway holds no key of its own. It fetches the public keys once, caches them, and refetches
 * when a token presents an unknown {@code kid} - so a key rotation needs no gateway restart and no
 * configuration change.
 */
@Configuration
public class JwtDecoderConfig {

    @Bean
    public ReactiveJwtDecoder reactiveJwtDecoder(GatewayProperties properties) {
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder
                .withJwkSetUri(properties.jwkSetUri())
                .build();

        // Signature alone is not enough: a token signed by the right key but issued for another
        // environment must still be refused.
        OAuth2TokenValidator<Jwt> validator =
                JwtValidators.createDefaultWithIssuer(properties.issuer());
        decoder.setJwtValidator(validator);

        return decoder;
    }
}
