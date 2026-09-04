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

        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.0", inclusive = false, message = "amount must be greater than 0")
        @Digits(integer = 10, fraction = 2, message = "amount must have at most 2 decimals")
        BigDecimal amount,

        @NotNull(message = "currency is required")
        @Pattern(regexp = "^[A-Z]{3}$", message = "currency must be an ISO-4217 code, e.g. EUR")
        String currency) {
}
