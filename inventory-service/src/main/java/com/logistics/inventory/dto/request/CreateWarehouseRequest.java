package com.logistics.inventory.dto.request;

import com.logistics.inventory.dto.common.AddressDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Coordinates are mandatory, not optional metadata: they are the input to the distance ranking the
 * order allocation engine performs, so a warehouse without them could never be chosen.
 */
public record CreateWarehouseRequest(

        @NotBlank(message = "code est obligatoire")
        @Pattern(regexp = "^[A-Z0-9-]{3,16}$",
                message = "code doit comporter de 3 à 16 caractères parmi A-Z, 0-9 ou le tiret")
        String code,

        @NotBlank(message = "name est obligatoire")
        @Size(max = 120, message = "name ne doit pas dépasser 120 caractères")
        String name,

        @NotNull(message = "address est obligatoire")
        @Valid AddressDto address,

        @NotNull(message = "latitude est obligatoire")
        @DecimalMin(value = "-90.0", message = "latitude doit être comprise entre -90 et 90")
        @DecimalMax(value = "90.0", message = "latitude doit être comprise entre -90 et 90")
        BigDecimal latitude,

        @NotNull(message = "longitude est obligatoire")
        @DecimalMin(value = "-180.0", message = "longitude doit être comprise entre -180 et 180")
        @DecimalMax(value = "180.0", message = "longitude doit être comprise entre -180 et 180")
        BigDecimal longitude) {
}
