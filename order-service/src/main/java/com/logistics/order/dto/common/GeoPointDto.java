package com.logistics.order.dto.common;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** A delivery point. Mandatory: the allocation engine ranks warehouses by distance to it. */
public record GeoPointDto(

        @NotNull(message = "latitude est obligatoire")
        @DecimalMin(value = "-90.0", message = "latitude doit être comprise entre -90 et 90")
        @DecimalMax(value = "90.0", message = "latitude doit être comprise entre -90 et 90")
        BigDecimal latitude,

        @NotNull(message = "longitude est obligatoire")
        @DecimalMin(value = "-180.0", message = "longitude doit être comprise entre -180 et 180")
        @DecimalMax(value = "180.0", message = "longitude doit être comprise entre -180 et 180")
        BigDecimal longitude) {
}
