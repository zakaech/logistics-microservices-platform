package com.logistics.inventory.dto.common;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AddressDto(

        @NotBlank(message = "line1 is required")
        @Size(max = 180, message = "line1 must not exceed 180 characters")
        String line1,

        @NotBlank(message = "city is required")
        @Size(max = 80, message = "city must not exceed 80 characters")
        String city,

        @NotBlank(message = "postalCode is required")
        @Size(max = 16, message = "postalCode must not exceed 16 characters")
        String postalCode,

        @NotBlank(message = "country is required")
        @Pattern(regexp = "^[A-Z]{2}$", message = "country must be an ISO-3166-1 alpha-2 code")
        String country) {
}
