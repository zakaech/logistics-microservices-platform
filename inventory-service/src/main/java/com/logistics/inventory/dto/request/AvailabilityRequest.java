package com.logistics.inventory.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * Availability snapshot request.
 *
 * <p>Answered with stock levels AND warehouse coordinates in one response, so the allocation engine
 * in order-service needs a single round trip rather than one call per warehouse.
 */
public record AvailabilityRequest(

        @NotEmpty(message = "productIds must contain at least one id")
        @Size(max = 100, message = "at most 100 productIds per request")
        Set<String> productIds) {
}
