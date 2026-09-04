package com.logistics.order.dto.response;

/** A strategy a caller may ask for, surfaced so the choice can be made knowingly. */
public record AllocationStrategyResponse(String name, String description, boolean isDefault) {
}
