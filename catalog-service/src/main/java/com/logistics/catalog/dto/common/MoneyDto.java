package com.logistics.catalog.dto.common;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

/**
 * A price, in requests and responses alike.
 *
 * <p>One record for both directions because the shape is genuinely identical; splitting it would
 * duplicate the validation rules with no benefit.
 */
public record MoneyDto(

        @NotNull(message = "amount est obligatoire")
        @DecimalMin(value = "0.0", inclusive = false, message = "amount doit être strictement supérieur à 0")
        @Digits(integer = 10, fraction = 2, message = "amount ne peut avoir plus de 2 décimales")
        BigDecimal amount,

        @NotNull(message = "currency est obligatoire")
        @Pattern(regexp = "^[A-Z]{3}$", message = "currency doit être un code ISO-4217, par exemple EUR")
        String currency) {
}
