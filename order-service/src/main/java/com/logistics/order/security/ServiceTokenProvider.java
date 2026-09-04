package com.logistics.order.security;

import com.logistics.order.config.DownstreamProperties;
import com.logistics.order.exception.UpstreamServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Obtains and caches this service's own access token.
 *
 * <p>Decision D9: order-service never replays the customer's token when calling other services. It
 * authenticates as itself, with {@code ROLE_SERVICE}, which is what allows the internal endpoints
 * of inventory-service to refuse everything else - including a valid customer token that reached
 * the gateway.
 *
 * <p>The token is cached until shortly before it expires. Fetching one per outbound call would turn
 * every order into three round trips to auth-service and make it a bottleneck for the whole
 * platform.
 */
@Slf4j
@Component
public class ServiceTokenProvider {

    /** Renew this early, so a token never expires in flight between the check and the call. */
    private static final Duration RENEWAL_MARGIN = Duration.ofSeconds(30);

    private final RestClient restClient;
    private final DownstreamProperties properties;
    private final Clock clock;
    private final ReentrantLock lock = new ReentrantLock();

    private String cachedToken;
    private Instant expiresAt = Instant.EPOCH;

    public ServiceTokenProvider(RestClient downstreamRestClient,
                                DownstreamProperties properties,
                                Clock clock) {
        this.restClient = downstreamRestClient;
        this.properties = properties;
        this.clock = clock;
    }

    /** A valid bearer token, fetched only when the cached one is missing or nearly expired. */
    public String currentToken() {
        if (isUsable()) {
            return cachedToken;
        }
        lock.lock();
        try {
            // Re-checked inside the lock: several threads may have queued on an expired token, and
            // only the first of them needs to actually fetch a new one.
            if (isUsable()) {
                return cachedToken;
            }
            fetchToken();
            return cachedToken;
        } finally {
            lock.unlock();
        }
    }

    /** Drops the cached token so the next call fetches a fresh one, after a 401 from downstream. */
    public void invalidate() {
        lock.lock();
        try {
            cachedToken = null;
            expiresAt = Instant.EPOCH;
        } finally {
            lock.unlock();
        }
    }

    private boolean isUsable() {
        return cachedToken != null && clock.instant().isBefore(expiresAt.minus(RENEWAL_MARGIN));
    }

    private void fetchToken() {
        if (properties.clientSecret() == null || properties.clientSecret().isBlank()) {
            throw new UpstreamServiceException("auth-service",
                    "No client secret configured for '" + properties.clientId()
                            + "'; service-to-service calls cannot be authenticated.");
        }
        try {
            Map<String, Object> response = restClient.post()
                    .uri("/api/v1/auth/token")
                    .body(Map.of("clientId", properties.clientId(),
                            "clientSecret", properties.clientSecret()))
                    .retrieve()
                    .body(Map.class);

            if (response == null || response.get("accessToken") == null) {
                throw new UpstreamServiceException("auth-service",
                        "Token endpoint returned no access token.");
            }

            this.cachedToken = (String) response.get("accessToken");
            long expiresIn = ((Number) response.getOrDefault("expiresIn", 900)).longValue();
            this.expiresAt = clock.instant().plusSeconds(expiresIn);

            log.debug("Obtained a service token for '{}', valid for {}s",
                    properties.clientId(), expiresIn);
        } catch (RestClientException e) {
            throw new UpstreamServiceException("auth-service",
                    "Could not obtain a service token: " + e.getMessage(), e);
        }
    }
}
