package com.dbtraining.reconx.model;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;

/**
 * ============================================================================
 * TICKET-ADV024 — Immutable value object: Money
 *
 * WHAT:    Record bundling a {@link BigDecimal} amount with a {@link Currency}.
 *          Used everywhere a monetary value crosses a boundary (DTO, event,
 *          metric).
 * HOW:     Compact constructor enforces: non-null amount, non-null currency,
 *          non-negative amount. {@link BigDecimal} (not double) prevents
 *          accumulating floating-point error on aggregations.
 * WHY:     Passing raw BigDecimal around loses currency context — a USD 100
 *          can be silently added to a EUR 100. Money makes the mismatch
 *          fail at the type level: {@code plus()} throws if currencies differ.
 * OBSERVE: {@code Money.of("100.00","USD").plus(Money.of("50","EUR"))} throws.
 *          {@code Money.of("100","USD").plus(Money.of("50","USD"))} returns 150 USD.
 * ============================================================================
 */
public record Money(BigDecimal amount, Currency currency) {

    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("Money amount cannot be negative: " + amount);
        }
    }

    /**
     * @param amount       the decimal amount, parsed via {@link BigDecimal#BigDecimal(String)}
     * @param currencyCode the ISO-4217 currency code, e.g. {@code "USD"}
     * @return a new {@code Money} of {@code amount} in {@code currencyCode}
     * @throws IllegalArgumentException if {@code currencyCode} is not a valid ISO-4217 code
     */
    public static Money of(String amount, String currencyCode) {
        return new Money(new BigDecimal(amount), Currency.getInstance(currencyCode));
    }

    /**
     * @param amount       the decimal amount
     * @param currencyCode the ISO-4217 currency code, e.g. {@code "USD"}
     * @return a new {@code Money} of {@code amount} in {@code currencyCode}
     * @throws IllegalArgumentException if {@code currencyCode} is not a valid ISO-4217 code
     */
    public static Money of(BigDecimal amount, String currencyCode) {
        return new Money(amount, Currency.getInstance(currencyCode));
    }

    /**
     * Add another Money of the same currency.
     * @param other the amount to add; must share this Money's currency
     * @return a new {@code Money} whose amount is {@code this.amount + other.amount}
     * @throws IllegalArgumentException if {@code other.currency()} differs from this currency
     */
    public Money plus(Money other) {
        Objects.requireNonNull(other, "other");
        if (!currency.equals(other.currency())) {
            throw new IllegalArgumentException(
                    "Money currency mismatch: %s cannot be added to %s"
                            .formatted(other.currency().getCurrencyCode(), currency.getCurrencyCode()));
        }
        return new Money(amount.add(other.amount()), currency);
    }

    /**
     * Scale this Money by a multiplier. Returns a new instance; never mutates.
     * @param multiplier the scalar to multiply this amount by
     * @return a new {@code Money} whose amount is {@code this.amount * multiplier}, same currency
     */
    public Money times(BigDecimal multiplier) {
        Objects.requireNonNull(multiplier, "multiplier");
        return new Money(amount.multiply(multiplier), currency);
    }
}
