package com.logistics.catalog.dto.common;

import jakarta.validation.constraints.Positive;

public record DimensionsDto(
        @Positive(message = "lengthMm must be positive") Integer lengthMm,
        @Positive(message = "widthMm must be positive") Integer widthMm,
        @Positive(message = "heightMm must be positive") Integer heightMm,
        @Positive(message = "weightG must be positive") Integer weightG) {
}
