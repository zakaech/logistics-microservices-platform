package com.logistics.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The routing table.
 *
 * <p>Declared in Java rather than in {@code application.yml} for two reasons: the property path for
 * gateway routes has moved between Spring Cloud versions, and a bean can be asserted on in a test.
 * The addresses themselves still come from configuration.
 *
 * <p>No {@code StripPrefix} filter: every service exposes the same {@code /api/v1} prefix as the
 * gateway, so a path is forwarded unchanged. That means a URL seen in a browser's network tab is the
 * URL the service actually received, which removes a whole class of debugging confusion.
 */
@Configuration
public class RouteConfig {

    @Bean
    public RouteLocator platformRoutes(RouteLocatorBuilder builder, GatewayProperties properties) {
        GatewayProperties.Services services = properties.services();

        return builder.routes()
                .route("auth-service", route -> route
                        .path("/api/v1/auth/**", "/api/v1/users/**")
                        .uri(services.auth()))
                .route("catalog-service", route -> route
                        .path("/api/v1/products/**", "/api/v1/categories/**")
                        .uri(services.catalog()))
                .route("inventory-service", route -> route
                        .path("/api/v1/warehouses/**", "/api/v1/stock/**", "/api/v1/inventory/**")
                        .uri(services.inventory()))
                .route("order-service", route -> route
                        .path("/api/v1/orders/**")
                        .uri(services.order()))
                .build();
    }
}
