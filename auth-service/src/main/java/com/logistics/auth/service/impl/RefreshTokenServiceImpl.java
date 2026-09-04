package com.logistics.auth.service.impl;

import com.logistics.auth.config.SecurityProperties;
import com.logistics.auth.domain.entity.RefreshToken;
import com.logistics.auth.domain.entity.User;
import com.logistics.auth.exception.InvalidTokenException;
import com.logistics.auth.repository.RefreshTokenRepository;
import com.logistics.auth.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenServiceImpl implements RefreshTokenService {

    /** 32 bytes of entropy: far beyond what is guessable, and 43 characters once base64url-encoded. */
    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository refreshTokenRepository;
    private final SecurityProperties securityProperties;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    @Transactional
    public IssuedRefreshToken issue(User user) {
        String rawValue = generateRawToken();
        Instant now = clock.instant();

        RefreshToken entity = RefreshToken.builder()
                .user(user)
                .tokenHash(hash(rawValue))
                .expiresAt(now.plus(securityProperties.jwt().refreshTokenTtl()))
                .revoked(false)
                .createdAt(now)
                .build();

        return new IssuedRefreshToken(rawValue, refreshTokenRepository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public RefreshToken verify(String rawToken) {
        RefreshToken token = refreshTokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new InvalidTokenException("Unknown refresh token."));

        if (!token.isUsableAt(clock.instant())) {
            throw new InvalidTokenException("The refresh token has expired or been revoked.");
        }
        return token;
    }

    @Override
    @Transactional
    public IssuedRefreshToken rotate(String rawToken) {
        RefreshToken current = verify(rawToken);
        current.revoke();
        refreshTokenRepository.save(current);
        return issue(current.getUser());
    }

    @Override
    @Transactional
    public void revoke(String rawToken) {
        // Logout is idempotent: an unknown or already-revoked token still means "you are logged
        // out". Failing here would only tell a caller whether a token ever existed.
        refreshTokenRepository.findByTokenHash(hash(rawToken)).ifPresent(token -> {
            token.revoke();
            refreshTokenRepository.save(token);
        });
    }

    @Override
    @Transactional
    public int purgeExpired() {
        int deleted = refreshTokenRepository.deleteAllExpiredBefore(clock.instant());
        if (deleted > 0) {
            log.info("Purged {} expired refresh token(s)", deleted);
        }
        return deleted;
    }

    private String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * SHA-256, not BCrypt.
     *
     * <p>A refresh token is 256 bits of uniform randomness, so it cannot be brute-forced or guessed
     * from a dictionary - the slow, salted hashing that protects human-chosen passwords buys nothing
     * here, and a lookup by hash must stay a single indexed read.
     */
    private String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available on this JVM", e);
        }
    }
}
