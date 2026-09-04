package com.logistics.catalog.dto.common;

import com.logistics.catalog.domain.enums.AttributeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AttributeDefinitionDto(

        @NotBlank(message = "key is required")
        @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_]{0,39}$",
                message = "key must be alphanumeric, start with a letter, max 40 characters")
        String key,

        @NotBlank(message = "label is required")
        @Size(max = 80, message = "label must not exceed 80 characters")
        String label,

        @NotNull(message = "type is required")
        AttributeType type,

        boolean required) {
}
