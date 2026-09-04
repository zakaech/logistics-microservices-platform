package com.logistics.catalog.domain.vo;

import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;

/**
 * An amount and its currency.
 *
 * <p>Stored as a Mongo {@code Decimal128}, not a double: binary floating point cannot represent
 * 349.90 exactly, and a catalogue price that drifts by a cent is a catalogue price nobody trusts.
 */
public record Money(
        @Field(targetType = FieldType.DECIMAL128) BigDecimal amount,
        String currency) {
}
