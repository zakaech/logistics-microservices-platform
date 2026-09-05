package com.logistics.order.dto.request;

import jakarta.validation.constraints.Size;

/** A reason is optional but recorded: the status history is what explains a cancellation later. */
public record CancelOrderRequest(
        @Size(max = 255, message = "reason ne doit pas dépasser 255 caractères") String reason) {
}
