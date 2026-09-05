package com.logistics.auth.service.impl;

import com.logistics.auth.config.SecurityProperties;
import com.logistics.auth.domain.entity.RefreshToken;
import com.logistics.auth.domain.entity.User;
import com.logistics.auth.exception.InvalidTokenException;
import com.logistics.auth.repository.RefreshTokenRepository;
import com.logistics.auth.service.RefreshTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the refresh token lifecycle.
 *
 * <p>The security-critical assertion is the first one: what reaches the database must never be the
 * value handed to the client.
 */
@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceImplTest {

    private static final Duration REFRESH_TTL = Duration.ofDays(7);

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private Instant now;
    private User user;
    private RefreshTokenServiceImpl service;

    @BeforeEach
    void setUp() {
        now = Instant.parse("2026-09-03T10:00:00Z");
        user = User.builder()
                .id(UUID.randomUUID())
                .email("aya@example.com")
                .passwordHash("irrelevant")
                .firstName("Aya")
                .lastName("Bennani")
                .enabled(true)
                .build();

        service = new RefreshTokenServiceImpl(
                refreshTokenRepository, properties(), Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("stores the hash, never the raw token")
    void storesOnlyTheHash() {
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RefreshTokenService.IssuedRefreshToken issued = service.issue(user);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        RefreshToken persisted = captor.getValue();

        assertThat(issued.rawValue()).isNotBlank();
        assertThat(persisted.getTokenHash())
                .isNotEqualTo(issued.rawValue())
                .isEqualTo(sha256Hex(issued.rawValue()))
                .hasSize(64);
    }

    @Test
    @DisplayName("sets the expiry from the configured TTL and the injected clock")
    void setsExpiryFromTtl() {
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RefreshToken persisted = service.issue(user).entity();

        assertThat(persisted.getCreatedAt()).isEqualTo(now);
        assertThat(persisted.getExpiresAt()).isEqualTo(now.plus(REFRESH_TTL));
        assertThat(persisted.isRevoked()).isFalse();
        assertThat(persisted.getUser()).isSameAs(user);
    }

    @Test
    @DisplayName("issues a different token every time")
    void issuesUniqueTokens() {
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.issue(user).rawValue())
                .isNotEqualTo(service.issue(user).rawValue());
    }

    @Test
    @DisplayName("verify rejects an unknown token")
    void verifyRejectsUnknownToken() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verify("whatever"))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("inconnu");
    }

    @Test
    @DisplayName("verify rejects a revoked token")
    void verifyRejectsRevokedToken() {
        RefreshToken revoked = storedToken(now.plus(Duration.ofDays(1)), true);
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> service.verify("raw"))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("expiré ou a été révoqué");
    }

    @Test
    @DisplayName("verify rejects an expired token")
    void verifyRejectsExpiredToken() {
        RefreshToken expired = storedToken(now.minusSeconds(1), false);
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.verify("raw"))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    @DisplayName("verify accepts a live token")
    void verifyAcceptsLiveToken() {
        RefreshToken live = storedToken(now.plus(Duration.ofDays(1)), false);
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(live));

        assertThat(service.verify("raw")).isSameAs(live);
    }

    @Test
    @DisplayName("rotation revokes the presented token and issues a new one")
    void rotationRevokesAndReissues() {
        RefreshToken live = storedToken(now.plus(Duration.ofDays(1)), false);
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(live));
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RefreshTokenService.IssuedRefreshToken rotated = service.rotate("raw");

        assertThat(live.isRevoked()).isTrue();
        assertThat(rotated.entity()).isNotSameAs(live);
        assertThat(rotated.entity().isRevoked()).isFalse();
        // One save for the revocation, one for the replacement.
        verify(refreshTokenRepository, times(2)).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("rotation of an already-used token fails, so replay is detected")
    void rotationOfRevokedTokenFails() {
        RefreshToken alreadyUsed = storedToken(now.plus(Duration.ofDays(1)), true);
        when(refreshTokenRepository.findByTokenHash(anyString()))
                .thenReturn(Optional.of(alreadyUsed));

        assertThatThrownBy(() -> service.rotate("raw"))
                .isInstanceOf(InvalidTokenException.class);
        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("logout is idempotent: revoking an unknown token is not an error")
    void revokeIsIdempotent() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatCode(() -> service.revoke("unknown")).doesNotThrowAnyException();
        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("purge deletes rows expired at the current clock instant")
    void purgeUsesTheInjectedClock() {
        when(refreshTokenRepository.deleteAllExpiredBefore(now)).thenReturn(3);

        assertThat(service.purgeExpired()).isEqualTo(3);
        verify(refreshTokenRepository).deleteAllExpiredBefore(now);
    }

    // --- helpers -----------------------------------------------------------

    private RefreshToken storedToken(Instant expiresAt, boolean revoked) {
        return RefreshToken.builder()
                .id(UUID.randomUUID())
                .user(user)
                .tokenHash("stored-hash")
                .expiresAt(expiresAt)
                .revoked(revoked)
                .createdAt(now.minus(Duration.ofHours(1)))
                .build();
    }

    private SecurityProperties properties() {
        return new SecurityProperties(
                new SecurityProperties.Jwt("logistics-auth-test", Duration.ofMinutes(15),
                        REFRESH_TTL, "test-key", null, null),
                Map.of(),
                new SecurityProperties.BootstrapAdmin(false, null, null));
    }

    private String sha256Hex(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
