package com.logistics.catalog.dto.common;

import jakarta.validation.constraints.Positive;

public record DimensionsDto(
        @Positive(message = "lengthMm doit être positif") Integer lengthMm,
        @Positive(message = "widthMm doit être positif") Integer widthMm,
        @Positive(message = "heightMm doit être positif") Integer heightMm,
        @Positive(message = "weightG doit être positif") Integer weightG) {
}
