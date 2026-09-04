package com.logistics.inventory.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * @param defaultTtl how long a reservation holds stock when the caller does not say. Long enough
 *                   for order-service to confirm, short enough that a crashed orchestrator does not
 *                   strand stock for hours.
 */
@Validated
@ConfigurationProperties(prefix = "logistics.reservation")
public record ReservationProperties(@NotNull Duration defaultTtl) {
}
