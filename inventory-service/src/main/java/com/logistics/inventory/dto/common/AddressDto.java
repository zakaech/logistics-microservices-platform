package com.logistics.inventory.dto.common;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AddressDto(

        @NotBlank(message = "line1 est obligatoire")
        @Size(max = 180, message = "line1 ne doit pas dépasser 180 caractères")
        String line1,

        @NotBlank(message = "city est obligatoire")
        @Size(max = 80, message = "city ne doit pas dépasser 80 caractères")
        String city,

        @NotBlank(message = "postalCode est obligatoire")
        @Size(max = 16, message = "postalCode ne doit pas dépasser 16 caractères")
        String postalCode,

        @NotBlank(message = "country est obligatoire")
        @Pattern(regexp = "^[A-Z]{2}$", message = "country doit être un code ISO-3166-1 alpha-2")
        String country) {
}
