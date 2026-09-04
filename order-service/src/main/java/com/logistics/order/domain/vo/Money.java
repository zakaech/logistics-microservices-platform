package com.logistics.order.domain.vo;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * An amount and its currency.
 *
 * <p>{@code NUMERIC(12,2)}, never a floating-point type: an order total that is off by a cent is an
 * invoice nobody can reconcile.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class Money {

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    public static Money of(BigDecimal amount, String currency) {
        return new Money(amount, currency);
    }

    public Money multiply(int quantity) {
        return new Money(amount.multiply(BigDecimal.valueOf(quantity)), currency);
    }

    public Money add(Money other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "Cannot add " + other.currency + " to " + currency + ".");
        }
        return new Money(amount.add(other.amount), currency);
    }

    public static Money zero(String currency) {
        return new Money(BigDecimal.ZERO.setScale(2), currency);
    }
}
