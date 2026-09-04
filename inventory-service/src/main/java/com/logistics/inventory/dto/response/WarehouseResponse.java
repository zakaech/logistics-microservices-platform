package com.logistics.inventory.dto.response;

import com.logistics.inventory.dto.common.AddressDto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WarehouseResponse(
        UUID id,
        String code,
        String name,
        AddressDto address,
        BigDecimal latitude,
        BigDecimal longitude,
        boolean active,
        long distinctProducts,
        Instant createdAt,
        Instant updatedAt) {
}
