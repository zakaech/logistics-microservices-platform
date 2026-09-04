package com.logistics.inventory.dto.response;

import com.logistics.inventory.domain.enums.ReservationStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A reservation and what it holds, grouped by warehouse. */
public record ReservationResponse(
        UUID id,
        String reference,
        ReservationStatus status,
        Instant expiresAt,
        Instant createdAt,
        Instant updatedAt,
        List<Segment> segments) {

    public record Segment(UUID warehouseId, String warehouseCode, List<Line> lines) {
    }

    public record Line(String productId, int quantity) {
    }
}
