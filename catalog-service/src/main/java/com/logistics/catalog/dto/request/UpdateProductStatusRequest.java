package com.logistics.catalog.dto.request;

import com.logistics.catalog.domain.enums.ProductStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateProductStatusRequest(
        @NotNull(message = "status is required") ProductStatus status) {
}
