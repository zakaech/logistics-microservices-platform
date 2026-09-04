package com.logistics.order.client.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * The hold inventory-service granted.
 *
 * @param expiresAt when the hold lapses if it is not confirmed. The orchestrator must finish before
 *                  then, and the sweeper on the other side releases it if this service dies.
 */
public record ReservationHandle(UUID id, String reference, String status, Instant expiresAt) {
}
