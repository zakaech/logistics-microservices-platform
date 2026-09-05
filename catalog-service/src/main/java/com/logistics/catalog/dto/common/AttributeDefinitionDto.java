package com.logistics.catalog.dto.common;

import com.logistics.catalog.domain.enums.AttributeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AttributeDefinitionDto(

        @NotBlank(message = "key est obligatoire")
        @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_]{0,39}$",
                message = "key doit être alphanumérique, commencer par une lettre, 40 caractères au maximum")
        String key,

        @NotBlank(message = "label est obligatoire")
        @Size(max = 80, message = "label ne doit pas dépasser 80 caractères")
        String label,

        @NotNull(message = "type est obligatoire")
        AttributeType type,

        boolean required) {
}
