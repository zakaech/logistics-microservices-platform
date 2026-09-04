package com.logistics.order.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param defaultStrategy      strategy used when a request expresses no preference
 * @param reservationTtlSeconds how long the stock hold should last, passed to inventory-service
 * @param maxAllocationAttempts how many times to re-plan when stock moves between reading
 *                              availability and reserving it. Bounded on purpose: retrying
 *                              indefinitely under contention turns a stockout into a stampede.
 */
@Validated
@ConfigurationProperties(prefix = "logistics.allocation")
public record AllocationProperties(
        @NotBlank String defaultStrategy,
        @Min(30) int reservationTtlSeconds,
        @Min(1) int maxAllocationAttempts) {
}
