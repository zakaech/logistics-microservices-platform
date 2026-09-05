package com.logistics.inventory.dto.request;

import com.logistics.inventory.dto.common.AddressDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Full replacement of a warehouse. The code is absent on purpose: it is the business key, printed
 * on labels and stored in past order allocations, so it is set once at creation.
 */
public record UpdateWarehouseRequest(

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
