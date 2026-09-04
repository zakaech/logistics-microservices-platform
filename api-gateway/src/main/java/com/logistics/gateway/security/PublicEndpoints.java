package com.logistics.gateway.security;

import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.List;

/**
 * The allow-list of endpoints reachable without a token.
 *
 * <p>Deliberately in code rather than in configuration: this is the security perimeter, and a change
 * to it belongs in a diff a reviewer reads, not in an environment variable someone can flip.
 *
 * <p>Method-aware on purpose. Browsing the catalogue is public, but creating a product is not, and a
 * path-only rule could not tell the two apart.
 */
@Component
public class PublicEndpoints {

    private static final PathPatternParser PARSER = new PathPatternParser();

    private final List<Rule> rules = List.of(
            // Obtaining or inspecting credentials cannot itself require credentials.
            rule(HttpMethod.POST, "/api/v1/auth/register"),
            rule(HttpMethod.POST, "/api/v1/auth/login"),
            rule(HttpMethod.POST, "/api/v1/auth/refresh"),
            rule(HttpMethod.POST, "/api/v1/auth/token"),
            rule(HttpMethod.POST, "/api/v1/auth/validate"),
            // Public keys are, by definition, public.
            rule(HttpMethod.GET, "/api/v1/auth/.well-known/jwks.json"),
            // The catalogue is browsable before signing in; every write stays protected.
            rule(HttpMethod.GET, "/api/v1/products/**"),
            rule(HttpMethod.GET, "/api/v1/categories/**"),
            rule(HttpMethod.GET, "/actuator/health/**"));

    public boolean isPublic(ServerHttpRequest request) {
        return rules.stream().anyMatch(rule -> rule.matches(request));
    }

    private static Rule rule(HttpMethod method, String pattern) {
        return new Rule(method, PARSER.parse(pattern));
    }

    private record Rule(HttpMethod method, PathPattern pattern) {

        boolean matches(ServerHttpRequest request) {
            return method.equals(request.getMethod())
                    && pattern.matches(request.getPath().pathWithinApplication());
        }
    }
}
