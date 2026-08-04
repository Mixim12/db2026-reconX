package com.dbtraining.reconx.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ============================================================================
 * TICKET-ADV024 — Tests for the immutable value object: Money
 *
 * WHAT:    Covers value-based equality, compact-constructor validation, the
 *          java.util.Currency typing, and the two arithmetic operations
 *          (plus / times) including the currency-mismatch failure mode.
 * WHY:     Money is the type every monetary boundary crossing relies on; if
 *          its invariants or immutability regress, silent money bugs follow.
 * ============================================================================
 */
class MoneyTest {

    // --- AC1: records with value-based equality -----------------------------

    @Test
    @DisplayName("AC1: two Money with same amount and currency are equal")
    void equalsIsValueBased() {
        Money a = Money.of("100", "USD");
        Money b = Money.of("100", "USD");

        assertThat(a).isEqualTo(b);
        assertThat(a).isNotSameAs(b);
    }

    @Test
    @DisplayName("AC1: equal Money instances share a hashCode")
    void hashCodeIsValueBased() {
        assertThat(Money.of("100", "USD").hashCode())
                .isEqualTo(Money.of("100", "USD").hashCode());
    }

    @Test
    @DisplayName("AC1: Money is a record, so it exposes no setters")
    void moneyIsARecord() {
        assertThat(Money.class.isRecord()).isTrue();
        assertThat(Money.class.getMethods())
                .noneMatch(m -> m.getName().startsWith("set"));
    }

    @Test
    @DisplayName("AC1: differing amount or currency breaks equality")
    void differingComponentsAreNotEqual() {
        assertThat(Money.of("100", "USD")).isNotEqualTo(Money.of("101", "USD"));
        assertThat(Money.of("100", "USD")).isNotEqualTo(Money.of("100", "EUR"));
    }

    // --- AC2: compact constructor validation --------------------------------

    @Test
    @DisplayName("AC2: negative amount is rejected with IllegalArgumentException")
    void rejectsNegativeAmount() {
        assertThatThrownBy(() -> Money.of("-0.01", "USD"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negative");
    }

    @Test
    @DisplayName("AC2: zero is a legal amount")
    void acceptsZeroAmount() {
        assertThat(Money.of("0", "USD").amount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("AC2: null amount is rejected with NullPointerException")
    void rejectsNullAmount() {
        assertThatThrownBy(() -> new Money(null, Currency.getInstance("USD")))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("amount");
    }

    @Test
    @DisplayName("AC2: null currency is rejected with NullPointerException")
    void rejectsNullCurrency() {
        assertThatThrownBy(() -> new Money(new BigDecimal("10"), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("currency");
    }

    // --- AC3: currency is java.util.Currency --------------------------------

    @Test
    @DisplayName("AC3: the currency component is java.util.Currency, not String")
    void currencyComponentIsJavaUtilCurrency() throws Exception {
        assertThat(Money.class.getRecordComponents()[1].getType())
                .isEqualTo(Currency.class);
        assertThat(Money.of("100", "USD").currency())
                .isInstanceOf(Currency.class)
                .isEqualTo(Currency.getInstance("USD"));
    }

    // --- AC4: plus returns a new instance, leaves operands untouched --------

    @Test
    @DisplayName("AC4: plus sums the amounts and keeps the currency")
    void plusSumsAmounts() {
        Money sum = Money.of("100", "USD").plus(Money.of("50", "USD"));

        assertThat(sum.amount()).isEqualByComparingTo(new BigDecimal("150"));
        assertThat(sum.currency()).isEqualTo(Currency.getInstance("USD"));
        assertThat(sum).isEqualTo(Money.of("150", "USD"));
    }

    @Test
    @DisplayName("AC4: plus mutates neither the receiver nor the argument")
    void plusIsImmutable() {
        Money receiver = Money.of("100", "USD");
        Money argument = Money.of("50", "USD");

        Money sum = receiver.plus(argument);

        assertThat(sum).isNotSameAs(receiver).isNotSameAs(argument);
        assertThat(receiver.amount()).isEqualByComparingTo(new BigDecimal("100"));
        assertThat(argument.amount()).isEqualByComparingTo(new BigDecimal("50"));
    }

    @Test
    @DisplayName("AC4: plus preserves decimal precision")
    void plusPreservesPrecision() {
        Money sum = Money.of("0.10", "USD").plus(Money.of("0.20", "USD"));

        assertThat(sum.amount()).isEqualByComparingTo(new BigDecimal("0.30"));
    }

    // --- AC5: plus rejects currency mismatch --------------------------------

    @Test
    @DisplayName("AC5: plus across currencies throws mentioning 'currency mismatch'")
    void plusRejectsCurrencyMismatch() {
        Money usd = Money.of("100", "USD");
        Money eur = Money.of("50", "EUR");

        assertThatThrownBy(() -> usd.plus(eur))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("currency mismatch")
                .hasMessageContaining("USD")
                .hasMessageContaining("EUR");
    }

    @Test
    @DisplayName("AC5: plus rejects a null operand")
    void plusRejectsNull() {
        assertThatThrownBy(() -> Money.of("100", "USD").plus(null))
                .isInstanceOf(NullPointerException.class);
    }

    // --- AC6: times ---------------------------------------------------------

    @Test
    @DisplayName("AC6: times multiplies the amount and keeps the currency")
    void timesMultipliesAmount() {
        Money product = Money.of("100", "USD").times(new BigDecimal("3"));

        assertThat(product.amount()).isEqualByComparingTo(new BigDecimal("300"));
        assertThat(product.currency()).isEqualTo(Currency.getInstance("USD"));
    }

    @Test
    @DisplayName("AC6: times returns a new instance and leaves the receiver alone")
    void timesIsImmutable() {
        Money receiver = Money.of("12.50", "GBP");

        Money product = receiver.times(new BigDecimal("4"));

        assertThat(product).isNotSameAs(receiver);
        assertThat(receiver.amount()).isEqualByComparingTo(new BigDecimal("12.50"));
        assertThat(product.amount()).isEqualByComparingTo(new BigDecimal("50"));
        assertThat(product.currency()).isEqualTo(Currency.getInstance("GBP"));
    }

    @Test
    @DisplayName("AC6: times by zero yields zero of the same currency")
    void timesByZero() {
        Money product = Money.of("99.99", "EUR").times(BigDecimal.ZERO);

        assertThat(product.amount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(product.currency()).isEqualTo(Currency.getInstance("EUR"));
    }

    @Test
    @DisplayName("AC6: times by a negative multiplier is rejected by the invariant")
    void timesByNegativeIsRejected() {
        assertThatThrownBy(() -> Money.of("100", "USD").times(new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negative");
    }

    @Test
    @DisplayName("AC6: times rejects a null multiplier")
    void timesRejectsNull() {
        assertThatThrownBy(() -> Money.of("100", "USD").times(null))
                .isInstanceOf(NullPointerException.class);
    }
}
