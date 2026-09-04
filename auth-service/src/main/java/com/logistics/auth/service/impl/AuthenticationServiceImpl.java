package com.logistics.auth.service.impl;

import com.logistics.auth.config.SecurityProperties;
import com.logistics.auth.domain.entity.Role;
import com.logistics.auth.domain.entity.User;
import com.logistics.auth.domain.enums.RoleName;
import com.logistics.auth.dto.request.LoginRequest;
import com.logistics.auth.dto.request.RefreshTokenRequest;
import com.logistics.auth.dto.request.RegisterRequest;
import com.logistics.auth.dto.request.ServiceTokenRequest;
import com.logistics.auth.dto.request.ValidateTokenRequest;
import com.logistics.auth.dto.response.TokenResponse;
import com.logistics.auth.dto.response.TokenValidationResponse;
import com.logistics.auth.dto.response.UserResponse;
import com.logistics.auth.exception.EmailAlreadyUsedException;
import com.logistics.auth.exception.InvalidCredentialsException;
import com.logistics.auth.exception.InvalidTokenException;
import com.logistics.auth.mapper.UserMapper;
import com.logistics.auth.repository.RoleRepository;
import com.logistics.auth.repository.UserRepository;
import com.logistics.auth.service.AuthenticationService;
import com.logistics.auth.service.RefreshTokenService;
import com.logistics.auth.service.TokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthenticationServiceImpl implements AuthenticationService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final RefreshTokenService refreshTokenService;
    private final SecurityProperties securityProperties;
    private final UserMapper userMapper;

    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = normaliseEmail(request.email());

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new EmailAlreadyUsedException(email);
        }

        // The role is decided here, never taken from the request: registration cannot grant
        // ROLE_ADMIN or ROLE_SERVICE, whatever the caller sends.
        Role defaultRole = roleRepository.findByName(RoleName.DEFAULT_REGISTRATION_ROLE)
                .orElseThrow(() -> new IllegalStateException(
                        "Role " + RoleName.DEFAULT_REGISTRATION_ROLE + " is missing; check the seed migration."));

        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .firstName(request.firstName().trim())
                .lastName(request.lastName().trim())
                .enabled(true)
                .build();
        user.addRole(defaultRole);

        User saved = userRepository.save(user);
        log.info("Registered user {}", saved.getId());
        return userMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(normaliseEmail(request.email()))
                .orElseThrow(() -> {
                    // Hash anyway so that a wrong email and a wrong password take comparable time:
                    // a fast rejection would reveal which emails exist.
                    passwordEncoder.encode(request.password());
                    return new InvalidCredentialsException();
                });

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        if (!user.isEnabled()) {
            // Same exception as a wrong password: an attacker learns nothing about the account.
            throw new InvalidCredentialsException();
        }

        return issueTokensFor(user);
    }

    @Override
    @Transactional
    public TokenResponse refresh(RefreshTokenRequest request) {
        RefreshTokenService.IssuedRefreshToken rotated =
                refreshTokenService.rotate(request.refreshToken());
        User user = rotated.entity().getUser();

        if (!user.isEnabled()) {
            throw new InvalidTokenException("The account is disabled.");
        }

        TokenService.IssuedToken accessToken = tokenService.issueAccessToken(
                user.getId().toString(), user.getEmail(), user.authorityNames());

        return TokenResponse.of(accessToken.value(), rotated.rawValue(),
                tokenService.accessTokenTtlSeconds());
    }

    @Override
    @Transactional
    public void logout(RefreshTokenRequest request) {
        refreshTokenService.revoke(request.refreshToken());
    }

    @Override
    public TokenResponse issueServiceToken(ServiceTokenRequest request) {
        SecurityProperties.ServiceClient client =
                securityProperties.serviceClients().get(request.clientId());

        if (client == null || !client.isUsable() || !secretMatches(client.secret(), request.clientSecret())) {
            log.warn("Rejected service token request for client '{}'", request.clientId());
            throw new InvalidCredentialsException();
        }

        Set<String> roles = client.roles().stream()
                .map(RoleName::name)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        // No refresh token: a service holds its credentials and simply asks for a new access token.
        TokenService.IssuedToken accessToken =
                tokenService.issueAccessToken(request.clientId(), null, roles);

        log.info("Issued a service token for client '{}'", request.clientId());
        return TokenResponse.ofAccessTokenOnly(accessToken.value(),
                tokenService.accessTokenTtlSeconds());
    }

    @Override
    public TokenValidationResponse validate(ValidateTokenRequest request) {
        try {
            TokenService.ValidatedToken token = tokenService.validate(request.token());
            return TokenValidationResponse.valid(
                    token.subject(), token.email(), token.roles(), token.expiresAt());
        } catch (InvalidTokenException e) {
            return TokenValidationResponse.invalid(e.getMessage());
        }
    }

    private TokenResponse issueTokensFor(User user) {
        TokenService.IssuedToken accessToken = tokenService.issueAccessToken(
                user.getId().toString(), user.getEmail(), user.authorityNames());
        RefreshTokenService.IssuedRefreshToken refreshToken = refreshTokenService.issue(user);

        return TokenResponse.of(accessToken.value(), refreshToken.rawValue(),
                tokenService.accessTokenTtlSeconds());
    }

    private String normaliseEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Constant-time comparison: a byte-by-byte {@code equals} leaks, through its timing, how many
     * leading characters of a guessed secret were correct.
     */
    private boolean secretMatches(String expected, String provided) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
    }
}
