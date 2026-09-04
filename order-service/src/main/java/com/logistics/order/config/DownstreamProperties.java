package com.logistics.order.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Where the services this one depends on live, and how long to wait for them.
 *
 * @param baseUri       the gateway, not the services directly: routing, tracing and edge policy
 *                      stay in one place, as decided in phase 0
 * @param connectTimeout how long to wait for a TCP connection. Short: a service that has not
 *                       accepted the connection in two seconds is down, not slow
 * @param readTimeout    how long to wait for a response. Bounded on purpose - without it a hung
 *                       downstream would hold this thread until the client gives up, and a handful
 *                       of those exhausts the pool
 * @param clientId       technical account used for service-to-service calls (decision D9)
 * @param clientSecret   its secret, from the environment, never from source
 */
@Validated
@ConfigurationProperties(prefix = "logistics.downstream")
public record DownstreamProperties(
        @NotBlank String baseUri,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout,
        @NotBlank String clientId,
        String clientSecret) {
}
