package com.logistics.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * CORS policy for the Angular application.
 *
 * <p>Expressed as a {@link CorsWebFilter} bean rather than through gateway properties: the property
 * path for CORS has moved between Spring Cloud versions, whereas this bean is stable.
 *
 * <p>Only the gateway carries this policy. Downstream services disable CORS entirely, because a
 * browser never talks to them directly.
 */
@Configuration
public class CorsConfig {

    private static final long MAX_AGE_SECONDS = 3600;

    @Bean
    public CorsWebFilter corsWebFilter(GatewayProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();

        // Explicit origins, never "*": allowCredentials is on so the browser can send the
        // httpOnly refresh cookie, and the two are mutually exclusive by specification.
        configuration.setAllowedOrigins(properties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
                "Authorization", "Content-Type", "Accept", "X-Request-Id", "Idempotency-Key"));
        // Without this the browser hides these headers from JavaScript.
        configuration.setExposedHeaders(List.of("Location", "X-Request-Id"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(MAX_AGE_SECONDS);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);

        return new CorsWebFilter(source);
    }
}
