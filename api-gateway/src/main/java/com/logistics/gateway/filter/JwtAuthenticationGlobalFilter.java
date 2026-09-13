package com.logistics.gateway.filter;

import com.logistics.gateway.security.PublicEndpoints;
import com.logistics.gateway.support.ProblemResponseWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Edge authentication and identity propagation.
 *
 * <p>Two jobs, deliberately in one filter because they must never be separated:
 *
 * <ol>
 *   <li><b>Validate</b> the Bearer token - signature against the JWKS, expiry, issuer. A request
 *       that fails is rejected here and never reaches a service.
 *   <li><b>Propagate</b> the resulting identity as {@code X-User-*} headers, after stripping any the
 *       client sent. Forging {@code X-User-Roles: ROLE_ADMIN} on an inbound request must be
 *       impossible, so the headers are removed unconditionally - on public routes too - and only
 *       ever re-added from claims this filter has just verified.
 * </ol>
 *
 * <p>The {@code Authorization} header is forwarded untouched: downstream services re-validate the
 * token themselves (decision D2) and never trust these headers for authorisation. The headers exist
 * for convenience and audit, not as a security mechanism - which is why stripping them
 * still matters.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationGlobalFilter implements GlobalFilter, Ordered {

    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_EMAIL_HEADER = "X-User-Email";
    public static final String USER_ROLES_HEADER = "X-User-Roles";
    public static final int ORDER = -100;

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String ROLES_CLAIM = "roles";
    private static final String EMAIL_CLAIM = "email";

    private final ReactiveJwtDecoder jwtDecoder;
    private final PublicEndpoints publicEndpoints;
    private final ProblemResponseWriter problemWriter;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        // A CORS preflight carries no credentials by design; CorsWebFilter has already answered it.
        if (HttpMethod.OPTIONS.equals(request.getMethod())) {
            return chain.filter(exchange);
        }

        ServerHttpRequest sanitised = withoutClientSuppliedIdentity(request);

        if (publicEndpoints.isPublic(request)) {
            return chain.filter(exchange.mutate().request(sanitised).build());
        }

        String token = extractBearerToken(request);
        if (token == null) {
            return problemWriter.unauthorized(exchange,
                    "Un jeton d'accès Bearer est requis pour appeler cet endpoint.");
        }

        return jwtDecoder.decode(token)
                .flatMap(jwt -> chain.filter(
                        exchange.mutate().request(withIdentityOf(sanitised, jwt)).build()))
                .onErrorResume(JwtException.class, error -> {
                    log.debug("Rejected a token on {} {}: {}",
                            request.getMethod(), request.getPath(), error.getMessage());
                    return problemWriter.unauthorized(exchange,
                            "Le jeton d'accès est invalide ou a expiré.");
                });
    }

    /**
     * Removes identity headers that arrived with the request. This runs on every path, including
     * public ones, so a header injected by a client can never survive as far as a service.
     */
    private ServerHttpRequest withoutClientSuppliedIdentity(ServerHttpRequest request) {
        return request.mutate()
                .headers(headers -> {
                    headers.remove(USER_ID_HEADER);
                    headers.remove(USER_EMAIL_HEADER);
                    headers.remove(USER_ROLES_HEADER);
                })
                .build();
    }

    private ServerHttpRequest withIdentityOf(ServerHttpRequest request, Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList(ROLES_CLAIM);
        String email = jwt.getClaimAsString(EMAIL_CLAIM);

        return request.mutate()
                .headers(headers -> {
                    headers.set(USER_ID_HEADER, jwt.getSubject());
                    if (email != null) {
                        headers.set(USER_EMAIL_HEADER, email);
                    }
                    if (roles != null && !roles.isEmpty()) {
                        headers.set(USER_ROLES_HEADER, String.join(",", roles));
                    }
                })
                .build();
    }

    private String extractBearerToken(ServerHttpRequest request) {
        String header = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
