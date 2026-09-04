package com.logistics.inventory.dto.request;

import jakarta.validation.constraints.NotNull;

/** Boxed Boolean so that a missing field fails validation instead of defaulting to false. */
public record UpdateWarehouseStatusRequest(
        @NotNull(message = "active is required") Boolean active) {
}
