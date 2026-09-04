package com.logistics.auth.service.impl;

import com.logistics.auth.config.SecurityProperties;
import com.logistics.auth.domain.entity.RefreshToken;
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
import com.logistics.auth.service.RefreshTokenService;
import com.logistics.auth.service.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the authentication use cases.
 *
 * <p>Several assertions are about what the service refuses to do: grant a role the caller asked for,
 * distinguish an unknown account from a wrong password, or accept an empty client secret. Those are
 * the behaviours a regression would silently break.
 */
@ExtendWith(MockitoExtension.class)
class AuthenticationServiceImplTest {

    private static final String RAW_PASSWORD = "Str0ngPassw0rd";
    private static final String HASHED_PASSWORD = "$2a$12$hashed";
    private static final String CLIENT_ID = "order-service";
    private static final String CLIENT_SECRET = "s3rvice-secret";

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private TokenService tokenService;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private UserMapper userMapper;

    @Captor
    private ArgumentCaptor<Set<String>> rolesCaptor;

    private Role clientRole;
    private User existingUser;
    private AuthenticationServiceImpl service;

    @BeforeEach
    void setUp() {
        clientRole = Role.builder().id(UUID.randomUUID()).name(RoleName.ROLE_CLIENT).build();
        existingUser = User.builder()
                .id(UUID.randomUUID())
                .email("aya@example.com")
                .passwordHash(HASHED_PASSWORD)
                .firstName("Aya")
                .lastName("Bennani")
                .enabled(true)
                .build();
        existingUser.addRole(clientRole);

        service = new AuthenticationServiceImpl(userRepository, roleRepository, passwordEncoder,
                tokenService, refreshTokenService, properties(), userMapper);
    }

    @Nested
    @DisplayName("register")
    class Register {

        @Test
        @DisplayName("rejects an email that already exists")
        void rejectsDuplicateEmail() {
            when(userRepository.existsByEmailIgnoreCase("aya@example.com")).thenReturn(true);

            assertThatThrownBy(() -> service.register(request("Aya@Example.com")))
                    .isInstanceOf(EmailAlreadyUsedException.class);

            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("normalises the email, hashes the password and grants ROLE_CLIENT only")
        void createsClientAccount() {
            when(userRepository.existsByEmailIgnoreCase(anyString())).thenReturn(false);
            when(roleRepository.findByName(RoleName.ROLE_CLIENT)).thenReturn(Optional.of(clientRole));
            when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(HASHED_PASSWORD);
            when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
            when(userMapper.toResponse(any(User.class))).thenReturn(userResponse());

            service.register(request("  Aya@Example.COM "));

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            User saved = captor.getValue();

            assertThat(saved.getEmail()).isEqualTo("aya@example.com");
            assertThat(saved.getPasswordHash()).isEqualTo(HASHED_PASSWORD);
            assertThat(saved.getPasswordHash()).isNotEqualTo(RAW_PASSWORD);
            assertThat(saved.isEnabled()).isTrue();
            assertThat(saved.authorityNames()).containsExactly("ROLE_CLIENT");
        }

        private RegisterRequest request(String email) {
            return new RegisterRequest(email, RAW_PASSWORD, "Aya", "Bennani");
        }
    }

    @Nested
    @DisplayName("login")
    class Login {

        @Test
        @DisplayName("reports the same failure for an unknown email as for a wrong password")
        void unknownEmailAndWrongPasswordAreIndistinguishable() {
            when(userRepository.findByEmailIgnoreCase("ghost@example.com"))
                    .thenReturn(Optional.empty());
            when(userRepository.findByEmailIgnoreCase("aya@example.com"))
                    .thenReturn(Optional.of(existingUser));
            when(passwordEncoder.matches("wrong", HASHED_PASSWORD)).thenReturn(false);

            Throwable unknownEmail = catchThrowable(
                    () -> service.login(new LoginRequest("ghost@example.com", "wrong")));
            Throwable wrongPassword = catchThrowable(
                    () -> service.login(new LoginRequest("aya@example.com", "wrong")));

            assertThat(unknownEmail).isInstanceOf(InvalidCredentialsException.class);
            assertThat(wrongPassword).isInstanceOf(InvalidCredentialsException.class);
            assertThat(unknownEmail.getMessage()).isEqualTo(wrongPassword.getMessage());
        }

        @Test
        @DisplayName("hashes a password even when the account does not exist, to keep timing flat")
        void hashesEvenForUnknownAccount() {
            when(userRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.login(new LoginRequest("ghost@example.com", RAW_PASSWORD)))
                    .isInstanceOf(InvalidCredentialsException.class);

            verify(passwordEncoder).encode(RAW_PASSWORD);
        }

        @Test
        @DisplayName("refuses a disabled account even with the right password")
        void refusesDisabledAccount() {
            existingUser.setEnabled(false);
            when(userRepository.findByEmailIgnoreCase(anyString()))
                    .thenReturn(Optional.of(existingUser));
            when(passwordEncoder.matches(RAW_PASSWORD, HASHED_PASSWORD)).thenReturn(true);

            assertThatThrownBy(() -> service.login(new LoginRequest("aya@example.com", RAW_PASSWORD)))
                    .isInstanceOf(InvalidCredentialsException.class);

            verify(tokenService, never()).issueAccessToken(anyString(), anyString(), anySet());
        }

        @Test
        @DisplayName("returns an access token and a refresh token on success")
        void returnsBothTokens() {
            when(userRepository.findByEmailIgnoreCase(anyString()))
                    .thenReturn(Optional.of(existingUser));
            when(passwordEncoder.matches(RAW_PASSWORD, HASHED_PASSWORD)).thenReturn(true);
            when(tokenService.issueAccessToken(
                    eq(existingUser.getId().toString()), eq("aya@example.com"), anySet()))
                    .thenReturn(new TokenService.IssuedToken("access-token", Instant.now()));
            when(tokenService.accessTokenTtlSeconds()).thenReturn(900L);
            when(refreshTokenService.issue(existingUser)).thenReturn(
                    new RefreshTokenService.IssuedRefreshToken("refresh-token", new RefreshToken()));

            TokenResponse response = service.login(new LoginRequest("aya@example.com", RAW_PASSWORD));

            assertThat(response.accessToken()).isEqualTo("access-token");
            assertThat(response.refreshToken()).isEqualTo("refresh-token");
            assertThat(response.tokenType()).isEqualTo("Bearer");
            assertThat(response.expiresIn()).isEqualTo(900L);
        }
    }

    @Nested
    @DisplayName("refresh")
    class Refresh {

        @Test
        @DisplayName("issues a new access token alongside the rotated refresh token")
        void issuesNewAccessToken() {
            RefreshToken rotatedEntity = RefreshToken.builder().user(existingUser).build();
            when(refreshTokenService.rotate("old-refresh")).thenReturn(
                    new RefreshTokenService.IssuedRefreshToken("new-refresh", rotatedEntity));
            when(tokenService.issueAccessToken(anyString(), anyString(), anySet()))
                    .thenReturn(new TokenService.IssuedToken("new-access", Instant.now()));
            when(tokenService.accessTokenTtlSeconds()).thenReturn(900L);

            TokenResponse response = service.refresh(new RefreshTokenRequest("old-refresh"));

            assertThat(response.accessToken()).isEqualTo("new-access");
            assertThat(response.refreshToken()).isEqualTo("new-refresh");
        }

        @Test
        @DisplayName("refuses to refresh for an account disabled since the token was issued")
        void refusesDisabledAccount() {
            existingUser.setEnabled(false);
            RefreshToken rotatedEntity = RefreshToken.builder().user(existingUser).build();
            when(refreshTokenService.rotate("old-refresh")).thenReturn(
                    new RefreshTokenService.IssuedRefreshToken("new-refresh", rotatedEntity));

            assertThatThrownBy(() -> service.refresh(new RefreshTokenRequest("old-refresh")))
                    .isInstanceOf(InvalidTokenException.class);
        }
    }

    @Nested
    @DisplayName("service tokens")
    class ServiceTokens {

        @Test
        @DisplayName("issues a ROLE_SERVICE token, without a refresh token")
        void issuesServiceToken() {
            when(tokenService.issueAccessToken(eq(CLIENT_ID), eq(null), anySet()))
                    .thenReturn(new TokenService.IssuedToken("service-token", Instant.now()));
            when(tokenService.accessTokenTtlSeconds()).thenReturn(900L);

            TokenResponse response = service.issueServiceToken(
                    new ServiceTokenRequest(CLIENT_ID, CLIENT_SECRET));

            assertThat(response.accessToken()).isEqualTo("service-token");
            assertThat(response.refreshToken()).isNull();

            verify(tokenService).issueAccessToken(eq(CLIENT_ID), eq(null), rolesCaptor.capture());
            assertThat(rolesCaptor.getValue()).containsExactly("ROLE_SERVICE");
        }

        @Test
        @DisplayName("rejects a wrong secret")
        void rejectsWrongSecret() {
            assertThatThrownBy(() -> service.issueServiceToken(
                    new ServiceTokenRequest(CLIENT_ID, "not-the-secret")))
                    .isInstanceOf(InvalidCredentialsException.class);
        }

        @Test
        @DisplayName("rejects an unknown client")
        void rejectsUnknownClient() {
            assertThatThrownBy(() -> service.issueServiceToken(
                    new ServiceTokenRequest("ghost-service", CLIENT_SECRET)))
                    .isInstanceOf(InvalidCredentialsException.class);
        }

        @Test
        @DisplayName("rejects a client whose secret was left unset in configuration")
        void rejectsClientWithBlankConfiguredSecret() {
            AuthenticationServiceImpl withBlankSecret = new AuthenticationServiceImpl(
                    userRepository, roleRepository, passwordEncoder, tokenService,
                    refreshTokenService, propertiesWithBlankClientSecret(), userMapper);

            assertThatThrownBy(() -> withBlankSecret.issueServiceToken(
                    new ServiceTokenRequest(CLIENT_ID, "")))
                    .isInstanceOf(InvalidCredentialsException.class);
        }
    }

    @Nested
    @DisplayName("validate")
    class Validate {

        @Test
        @DisplayName("reports an invalid token instead of throwing")
        void reportsInvalidToken() {
            when(tokenService.validate("bad")).thenThrow(new InvalidTokenException("expired"));

            TokenValidationResponse response = service.validate(new ValidateTokenRequest("bad"));

            assertThat(response.valid()).isFalse();
            assertThat(response.error()).isEqualTo("expired");
            assertThat(response.subject()).isNull();
        }

        @Test
        @DisplayName("returns the claims of a valid token")
        void returnsClaims() {
            Instant expiry = Instant.parse("2026-09-03T10:15:00Z");
            when(tokenService.validate("good")).thenReturn(new TokenService.ValidatedToken(
                    "user-id", "aya@example.com", List.of("ROLE_CLIENT"), expiry));

            TokenValidationResponse response = service.validate(new ValidateTokenRequest("good"));

            assertThat(response.valid()).isTrue();
            assertThat(response.subject()).isEqualTo("user-id");
            assertThat(response.roles()).containsExactly("ROLE_CLIENT");
            assertThat(response.expiresAt()).isEqualTo(expiry);
            assertThat(response.error()).isNull();
        }
    }

    // --- helpers -----------------------------------------------------------

    private UserResponse userResponse() {
        return new UserResponse(UUID.randomUUID(), "aya@example.com", "Aya", "Bennani",
                true, Set.of(RoleName.ROLE_CLIENT), Instant.now());
    }

    private SecurityProperties properties() {
        return new SecurityProperties(
                jwtProperties(),
                Map.of(CLIENT_ID, new SecurityProperties.ServiceClient(
                        CLIENT_SECRET, Set.of(RoleName.ROLE_SERVICE))),
                new SecurityProperties.BootstrapAdmin(false, null, null));
    }

    private SecurityProperties propertiesWithBlankClientSecret() {
        return new SecurityProperties(
                jwtProperties(),
                Map.of(CLIENT_ID, new SecurityProperties.ServiceClient(
                        "", Set.of(RoleName.ROLE_SERVICE))),
                new SecurityProperties.BootstrapAdmin(false, null, null));
    }

    private SecurityProperties.Jwt jwtProperties() {
        return new SecurityProperties.Jwt("logistics-auth-test", Duration.ofMinutes(15),
                Duration.ofDays(7), "test-key", null, null);
    }
}
