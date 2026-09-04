package com.logistics.auth.service.impl;

import com.logistics.auth.config.SecurityProperties;
import com.logistics.auth.exception.InvalidTokenException;
import com.logistics.auth.security.RsaKeyLoader;
import com.logistics.auth.service.TokenService;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.KeyPair;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for token issuance and validation.
 *
 * <p>No Spring context and no database: the encoder and decoder are built by hand from a generated
 * key pair, and the clock is injected. That is what makes it possible to assert on expiry without
 * the test ever sleeping.
 */
class JwtTokenServiceTest {

    private static final String ISSUER = "logistics-auth-test";
    private static final String KEY_ID = "test-key";
    private static final Duration ACCESS_TTL = Duration.ofMinutes(15);

    private static final String SUBJECT = "3f9a4d2e-6b1c-4c8a-9f0d-2b7e5a1c3d44";
    private static final String EMAIL = "aya@example.com";
    private static final Set<String> ROLES = Set.of("ROLE_CLIENT");

    private KeyPair keyPair;
    private Instant now;
    private JwtTokenService tokenService;

    @BeforeEach
    void setUp() {
        keyPair = RsaKeyLoader.generateKeyPair();
        now = Instant.now();
        tokenService = buildService(keyPair, Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("issues a token carrying subject, email, roles, issuer and expiry")
    void issuesTokenWithExpectedClaims() {
        TokenService.IssuedToken issued = tokenService.issueAccessToken(SUBJECT, EMAIL, ROLES);

        assertThat(issued.value()).isNotBlank();
        assertThat(issued.expiresAt()).isEqualTo(now.plus(ACCESS_TTL));

        TokenService.ValidatedToken validated = tokenService.validate(issued.value());
        assertThat(validated.subject()).isEqualTo(SUBJECT);
        assertThat(validated.email()).isEqualTo(EMAIL);
        assertThat(validated.roles()).containsExactly("ROLE_CLIENT");
        assertThat(validated.expiresAt()).isEqualTo(now.plus(ACCESS_TTL).truncatedTo(ChronoUnit.SECONDS));
    }

    @Test
    @DisplayName("advertises the key id in the header so a resource server can pick the right key")
    void putsKeyIdInHeader() {
        String token = tokenService.issueAccessToken(SUBJECT, EMAIL, ROLES).value();

        Jwt decoded = decoderFor(keyPair).decode(token);
        assertThat(decoded.getHeaders()).containsEntry("kid", KEY_ID);
        assertThat(decoded.getHeaders()).containsEntry("alg", "RS256");
        assertThat(decoded.getId()).isNotBlank();
    }

    @Test
    @DisplayName("omits the email claim for a technical account")
    void omitsEmailForServiceToken() {
        String token = tokenService
                .issueAccessToken("order-service", null, Set.of("ROLE_SERVICE"))
                .value();

        TokenService.ValidatedToken validated = tokenService.validate(token);
        assertThat(validated.subject()).isEqualTo("order-service");
        assertThat(validated.email()).isNull();
        assertThat(validated.roles()).containsExactly("ROLE_SERVICE");
    }

    @Test
    @DisplayName("rejects a token signed with another key - the point of asymmetric signing")
    void rejectsTokenSignedWithAnotherKey() {
        JwtTokenService attacker = buildService(RsaKeyLoader.generateKeyPair(),
                Clock.fixed(now, ZoneOffset.UTC));
        String forged = attacker.issueAccessToken(SUBJECT, EMAIL, Set.of("ROLE_ADMIN")).value();

        assertThatThrownBy(() -> tokenService.validate(forged))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    @DisplayName("rejects an expired token")
    void rejectsExpiredToken() {
        JwtTokenService pastIssuer = buildService(keyPair,
                Clock.fixed(now.minus(Duration.ofHours(2)), ZoneOffset.UTC));
        String expired = pastIssuer.issueAccessToken(SUBJECT, EMAIL, ROLES).value();

        assertThatThrownBy(() -> tokenService.validate(expired))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    @DisplayName("rejects a syntactically invalid token")
    void rejectsGarbage() {
        assertThatThrownBy(() -> tokenService.validate("not-a-jwt"))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    @DisplayName("reports the access token lifetime in seconds")
    void reportsTtlInSeconds() {
        assertThat(tokenService.accessTokenTtlSeconds()).isEqualTo(900L);
    }

    // --- helpers -----------------------------------------------------------

    private JwtTokenService buildService(KeyPair pair, Clock clock) {
        RSAKey rsaKey = rsaKey(pair);
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(rsaKey)));
        return new JwtTokenService(encoder, decoderFor(pair), properties(), clock);
    }

    private JwtDecoder decoderFor(KeyPair pair) {
        NimbusJwtDecoder decoder =
                NimbusJwtDecoder.withPublicKey((RSAPublicKey) pair.getPublic()).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(ISSUER));
        return decoder;
    }

    private RSAKey rsaKey(KeyPair pair) {
        return new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                .privateKey((RSAPrivateKey) pair.getPrivate())
                .keyID(KEY_ID)
                .build();
    }

    private SecurityProperties properties() {
        return new SecurityProperties(
                new SecurityProperties.Jwt(ISSUER, ACCESS_TTL, Duration.ofDays(7), KEY_ID, null, null),
                Map.of(),
                new SecurityProperties.BootstrapAdmin(false, null, null));
    }
}
