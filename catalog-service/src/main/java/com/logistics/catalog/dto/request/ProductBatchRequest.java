package com.logistics.catalog.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * Bulk lookup by id, used by order-service to build order lines in a single call.
 *
 * <p>Capped at 100: the N+1 problem exists over HTTP too, but so does the opposite abuse of asking
 * for the whole catalogue in one request.
 */
public record ProductBatchRequest(

        @NotEmpty(message = "productIds must contain at least one id")
        @Size(max = 100, message = "at most 100 productIds per request")
        Set<String> productIds) {
}
