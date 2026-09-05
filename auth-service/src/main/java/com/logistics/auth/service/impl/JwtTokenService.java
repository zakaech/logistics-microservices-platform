package com.logistics.auth.service.impl;

import com.logistics.auth.config.SecurityProperties;
import com.logistics.auth.exception.InvalidTokenException;
import com.logistics.auth.service.TokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * RS256 implementation backed by Spring Security's Nimbus encoder and decoder.
 *
 * <p>The {@link Clock} is injected rather than read from {@code Instant.now()} so that expiry
 * behaviour is testable without making the test sleep.
 */
@Service
@RequiredArgsConstructor
public class JwtTokenService implements TokenService {

    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_EMAIL = "email";

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;
    private final SecurityProperties securityProperties;
    private final Clock clock;

    @Override
    public IssuedToken issueAccessToken(String subject, String email, Set<String> roles) {
        SecurityProperties.Jwt config = securityProperties.jwt();
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(config.accessTokenTtl());

        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(config.issuer())
                .subject(subject)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                // A unique id per token, so an individual token can be traced in the logs and,
                // later, denied without revoking every token of the same user.
                .id(UUID.randomUUID().toString())
                .claim(CLAIM_ROLES, List.copyOf(roles));

        if (email != null) {
            claims.claim(CLAIM_EMAIL, email);
        }

        // The kid in the header is what lets a resource server pick the right key from the JWKS
        // after a key rotation.
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256)
                .keyId(config.keyId())
                .build();

        String value = jwtEncoder.encode(JwtEncoderParameters.from(header, claims.build()))
                .getTokenValue();
        return new IssuedToken(value, expiresAt);
    }

    @Override
    public ValidatedToken validate(String rawToken) {
        try {
            Jwt jwt = jwtDecoder.decode(rawToken);
            return new ValidatedToken(
                    jwt.getSubject(),
                    jwt.getClaimAsString(CLAIM_EMAIL),
                    jwt.getClaimAsStringList(CLAIM_ROLES),
                    jwt.getExpiresAt());
        } catch (JwtException e) {
            throw new InvalidTokenException("Le jeton est invalide ou a expiré.", e);
        }
    }

    @Override
    public long accessTokenTtlSeconds() {
        return securityProperties.jwt().accessTokenTtl().toSeconds();
    }
}
