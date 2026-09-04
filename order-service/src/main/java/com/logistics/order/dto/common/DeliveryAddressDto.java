package com.logistics.order.dto.common;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record DeliveryAddressDto(

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
        String country,

        @NotNull(message = "latitude is required")
        @DecimalMin(value = "-90.0", message = "latitude must be between -90 and 90")
        @DecimalMax(value = "90.0", message = "latitude must be between -90 and 90")
        BigDecimal latitude,

        @NotNull(message = "longitude is required")
        @DecimalMin(value = "-180.0", message = "longitude must be between -180 and 180")
        @DecimalMax(value = "180.0", message = "longitude must be between -180 and 180")
        BigDecimal longitude) {
}
