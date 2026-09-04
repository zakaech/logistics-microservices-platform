package com.logistics.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logistics.gateway.security.PublicEndpoints;
import com.logistics.gateway.support.ProblemResponseWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for edge authentication.
 *
 * <p>The first two tests are the important ones: they pin down that a client cannot inject its own
 * identity headers, on a protected route or a public one.
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationGlobalFilterTest {

    private static final String USER_ID = "3f9a4d2e-6b1c-4c8a-9f0d-2b7e5a1c3d44";

    @Mock
    private ReactiveJwtDecoder jwtDecoder;

    private JwtAuthenticationGlobalFilter filter;
    private AtomicReference<ServerHttpRequest> forwarded;
    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        ProblemResponseWriter writer =
                new ProblemResponseWriter(new ObjectMapper().findAndRegisterModules());
        filter = new JwtAuthenticationGlobalFilter(jwtDecoder, new PublicEndpoints(), writer);

        forwarded = new AtomicReference<>();
        chain = exchange -> {
            forwarded.set(exchange.getRequest());
            return Mono.empty();
        };
    }

    @Test
    @DisplayName("strips a client-supplied X-User-Roles header on a protected route")
    void stripsForgedIdentityHeadersOnProtectedRoute() {
        when(jwtDecoder.decode("good-token")).thenReturn(Mono.just(clientJwt()));

        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .get("/api/v1/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer good-token")
                .header(JwtAuthenticationGlobalFilter.USER_ID_HEADER, "attacker-id")
                .header(JwtAuthenticationGlobalFilter.USER_ROLES_HEADER, "ROLE_ADMIN"));

        filter.filter(exchange, chain).block();

        HttpHeaders headers = forwarded.get().getHeaders();
        // The forged values are gone; what remains comes from the verified token.
        assertThat(headers.getFirst(JwtAuthenticationGlobalFilter.USER_ID_HEADER))
                .isEqualTo(USER_ID);
        assertThat(headers.getFirst(JwtAuthenticationGlobalFilter.USER_ROLES_HEADER))
                .isEqualTo("ROLE_CLIENT");
    }

    @Test
    @DisplayName("strips client-supplied identity headers on a public route too")
    void stripsForgedIdentityHeadersOnPublicRoute() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .get("/api/v1/products/8f2b")
                .header(JwtAuthenticationGlobalFilter.USER_ID_HEADER, "attacker-id")
                .header(JwtAuthenticationGlobalFilter.USER_ROLES_HEADER, "ROLE_ADMIN"));

        filter.filter(exchange, chain).block();

        HttpHeaders headers = forwarded.get().getHeaders();
        assertThat(headers.getFirst(JwtAuthenticationGlobalFilter.USER_ID_HEADER)).isNull();
        assertThat(headers.getFirst(JwtAuthenticationGlobalFilter.USER_ROLES_HEADER)).isNull();
    }

    @Test
    @DisplayName("propagates subject, email and roles from the verified token")
    void propagatesVerifiedIdentity() {
        when(jwtDecoder.decode("good-token")).thenReturn(Mono.just(clientJwt()));

        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .get("/api/v1/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer good-token"));

        filter.filter(exchange, chain).block();

        HttpHeaders headers = forwarded.get().getHeaders();
        assertThat(headers.getFirst(JwtAuthenticationGlobalFilter.USER_ID_HEADER)).isEqualTo(USER_ID);
        assertThat(headers.getFirst(JwtAuthenticationGlobalFilter.USER_EMAIL_HEADER))
                .isEqualTo("aya@example.com");
        assertThat(headers.getFirst(JwtAuthenticationGlobalFilter.USER_ROLES_HEADER))
                .isEqualTo("ROLE_CLIENT");
        // The token itself is forwarded: services re-validate it rather than trusting the headers.
        assertThat(headers.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer good-token");
    }

    @Test
    @DisplayName("rejects a protected route with no token")
    void rejectsMissingToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/orders"));

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(forwarded.get()).isNull();
    }

    @Test
    @DisplayName("rejects a token the decoder refuses, without calling the service")
    void rejectsInvalidToken() {
        when(jwtDecoder.decode("bad-token"))
                .thenReturn(Mono.error(new JwtException("signature mismatch")));

        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .get("/api/v1/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer bad-token"));

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(forwarded.get()).isNull();
    }

    @Test
    @DisplayName("ignores an Authorization header that is not a Bearer token")
    void rejectsNonBearerAuthorization() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .get("/api/v1/orders")
                .header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz"));

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("lets a public route through without a token")
    void allowsPublicRouteWithoutToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/auth/login"));

        filter.filter(exchange, chain).block();

        assertThat(forwarded.get()).isNotNull();
    }

    @Test
    @DisplayName("lets a CORS preflight through untouched")
    void allowsPreflight() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.options("/api/v1/orders"));

        filter.filter(exchange, chain).block();

        assertThat(forwarded.get()).isNotNull();
    }

    private Jwt clientJwt() {
        Instant now = Instant.now();
        return Jwt.withTokenValue("good-token")
                .header("alg", "RS256")
                .header("kid", "logistics-auth-key")
                .subject(USER_ID)
                .claim("email", "aya@example.com")
                .claim("roles", List.of("ROLE_CLIENT"))
                .issuedAt(now)
                .expiresAt(now.plus(15, ChronoUnit.MINUTES))
                .build();
    }
}
