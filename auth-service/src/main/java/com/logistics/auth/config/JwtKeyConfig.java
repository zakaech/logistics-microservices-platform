package com.logistics.auth.config;

import com.logistics.auth.security.RsaKeyLoader;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.KeyPair;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

/**
 * RSA key material and the JOSE beans built from it.
 *
 * <p>Decision D8: tokens are signed with RS256. Only this service holds the private key; every other
 * component validates signatures with the public key published at
 * {@code /api/v1/auth/.well-known/jwks.json}. There is no shared secret anywhere in the platform, so
 * compromising a business service does not allow forging a token.
 */
@Slf4j
@Configuration
public class JwtKeyConfig {

    /**
     * The signing key, exposed as a bean so the JWKS endpoint can publish its public half.
     *
     * <p>When no key pair is configured, an ephemeral one is generated. That keeps a first
     * {@code docker compose up} working with no setup, at the cost of invalidating every issued
     * token on restart - hence the warning.
     */
    @Bean
    public RSAKey rsaSigningKey(SecurityProperties properties) {
        SecurityProperties.Jwt jwt = properties.jwt();
        RSAPublicKey publicKey;
        RSAPrivateKey privateKey;

        if (jwt.hasConfiguredKeyPair()) {
            publicKey = RsaKeyLoader.loadPublicKey(jwt.publicKey());
            privateKey = RsaKeyLoader.loadPrivateKey(jwt.privateKey());
            log.info("Loaded configured RSA signing key '{}'", jwt.keyId());
        } else {
            KeyPair generated = RsaKeyLoader.generateKeyPair();
            publicKey = (RSAPublicKey) generated.getPublic();
            privateKey = (RSAPrivateKey) generated.getPrivate();
            log.warn("No RSA key pair configured: generated an EPHEMERAL one. "
                    + "Every restart invalidates previously issued tokens. "
                    + "Set JWT_PRIVATE_KEY and JWT_PUBLIC_KEY outside development.");
        }

        return new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(jwt.keyId())
                .build();
    }

    @Bean
    public JwtEncoder jwtEncoder(RSAKey rsaSigningKey) {
        ImmutableJWKSet<SecurityContext> jwkSource =
                new ImmutableJWKSet<>(new JWKSet(rsaSigningKey));
        return new NimbusJwtEncoder(jwkSource);
    }

    /**
     * Decoder used to protect this service's own endpoints and to back the token validation
     * endpoint. Validates the signature, the expiry and the issuer.
     */
    @Bean
    public JwtDecoder jwtDecoder(RSAKey rsaSigningKey, SecurityProperties properties) {
        try {
            NimbusJwtDecoder decoder =
                    NimbusJwtDecoder.withPublicKey(rsaSigningKey.toRSAPublicKey()).build();
            decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.jwt().issuer()));
            return decoder;
        } catch (JOSEException e) {
            throw new IllegalStateException("Unable to build the JWT decoder from the signing key", e);
        }
    }
}
