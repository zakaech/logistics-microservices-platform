package com.logistics.order.dto.common;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record DeliveryAddressDto(

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
        String country,

        @NotNull(message = "latitude est obligatoire")
        @DecimalMin(value = "-90.0", message = "latitude doit être comprise entre -90 et 90")
        @DecimalMax(value = "90.0", message = "latitude doit être comprise entre -90 et 90")
        BigDecimal latitude,

        @NotNull(message = "longitude est obligatoire")
        @DecimalMin(value = "-180.0", message = "longitude doit être comprise entre -180 et 180")
        @DecimalMax(value = "180.0", message = "longitude doit être comprise entre -180 et 180")
        BigDecimal longitude) {
}
