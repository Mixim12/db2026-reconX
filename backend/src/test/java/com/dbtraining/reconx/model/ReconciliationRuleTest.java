package com.dbtraining.reconx.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class ReconciliationRuleTest {

    private static final BigDecimal TEN_UNITS = BigDecimal.TEN;

    @ParameterizedTest(name = "rule={0} priceA={1} priceB={2} qtyA={3} qtyB={4} expected={5}")
    @CsvSource({
            "EXACT,                100.00, 100.00, 10, 10, true",
            "EXACT,                100.00, 100.01, 10, 10, false",
            "PRICE_TOLERANCE_1PCT, 100.00, 100.50, 10, 10, true",
            "PRICE_TOLERANCE_1PCT, 100.00, 102.00, 10, 10, false",
            "QTY_TOLERANCE_5UNITS, 100.00, 100.00, 10, 14, true",
            "QTY_TOLERANCE_5UNITS, 100.00, 100.00, 10, 16, false",
            "LOOSE,                100.00, 104.00, 10, 18, true",
            // AC4 — price within tolerance, quantity far outside it.
            "LOOSE,                100.00, 104.00, 10, 40, false",
            // AC4 — quantity within tolerance, price far outside it.
            "LOOSE,                100.00, 120.00, 10, 12, false"
    })
    void matches(ReconciliationRule rule, BigDecimal pa, BigDecimal pb,
                 BigDecimal qa, BigDecimal qb, boolean expected) {
        assertThat(rule.matches(pa, qa, pb, qb)).isEqualTo(expected);
    }

    // ------------------------------------------------------------------
    // AC1 — constants exist and carry the documented BigDecimal thresholds.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("AC1: every constant carries its documented BigDecimal thresholds")
    void constantsCarryBigDecimalThresholds() {
        assertThat(ReconciliationRule.values())
                .hasSizeGreaterThanOrEqualTo(4)
                .contains(ReconciliationRule.EXACT,
                        ReconciliationRule.PRICE_TOLERANCE_1PCT,
                        ReconciliationRule.PRICE_TOLERANCE_50BPS,
                        ReconciliationRule.QTY_TOLERANCE_5UNITS,
                        ReconciliationRule.LOOSE);

        assertThat(ReconciliationRule.EXACT.priceTolerancePct()).isEqualByComparingTo("0");
        assertThat(ReconciliationRule.EXACT.qtyToleranceAbs()).isEqualByComparingTo("0");

        assertThat(ReconciliationRule.PRICE_TOLERANCE_1PCT.priceTolerancePct()).isEqualByComparingTo("0.01");
        assertThat(ReconciliationRule.PRICE_TOLERANCE_1PCT.qtyToleranceAbs()).isEqualByComparingTo("0");

        assertThat(ReconciliationRule.PRICE_TOLERANCE_50BPS.priceTolerancePct()).isEqualByComparingTo("0.005");
        assertThat(ReconciliationRule.PRICE_TOLERANCE_50BPS.qtyToleranceAbs()).isEqualByComparingTo("0");

        assertThat(ReconciliationRule.QTY_TOLERANCE_5UNITS.priceTolerancePct()).isEqualByComparingTo("0");
        assertThat(ReconciliationRule.QTY_TOLERANCE_5UNITS.qtyToleranceAbs()).isEqualByComparingTo("5");

        assertThat(ReconciliationRule.LOOSE.priceTolerancePct()).isEqualByComparingTo("0.05");
        assertThat(ReconciliationRule.LOOSE.qtyToleranceAbs()).isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("AC1: thresholds are BigDecimal, never a boxed floating-point type")
    void thresholdAccessorsReturnBigDecimal() {
        assertThat(ReconciliationRule.LOOSE.priceTolerancePct()).isInstanceOf(BigDecimal.class);
        assertThat(ReconciliationRule.LOOSE.qtyToleranceAbs()).isInstanceOf(BigDecimal.class);
    }

    // ------------------------------------------------------------------
    // AC3 — no binary floating point anywhere in the production source.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("AC3: production source declares no double or float")
    void productionSourceContainsNoBinaryFloatingPointTypes() throws IOException {
        String source = Files.readString(
                Path.of("src/main/java/com/dbtraining/reconx/model/ReconciliationRule.java"));

        assertThat(source).isNotBlank();
        assertThat(Pattern.compile("\\b(double|float)\\b").matcher(source).find())
                .as("ReconciliationRule.java must not mention double or float")
                .isFalse();
    }

    // ------------------------------------------------------------------
    // AC4 — both price AND quantity must be within tolerance.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("AC4: price inside tolerance but quantity outside it does not match")
    void priceWithinToleranceButQuantityOutsideDoesNotMatch() {
        boolean result = ReconciliationRule.LOOSE.matches(
                new BigDecimal("100.00"), TEN_UNITS,
                new BigDecimal("102.00"), new BigDecimal("40"));

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("AC4: quantity inside tolerance but price outside it does not match")
    void quantityWithinToleranceButPriceOutsideDoesNotMatch() {
        boolean result = ReconciliationRule.LOOSE.matches(
                new BigDecimal("100.00"), TEN_UNITS,
                new BigDecimal("150.00"), new BigDecimal("12"));

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("AC4: both inside tolerance matches")
    void bothWithinToleranceMatches() {
        boolean result = ReconciliationRule.LOOSE.matches(
                new BigDecimal("100.00"), TEN_UNITS,
                new BigDecimal("102.00"), new BigDecimal("12"));

        assertThat(result).isTrue();
    }

    // ------------------------------------------------------------------
    // AC5 — drift is absolute, so the sign of the difference is irrelevant.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("AC5: price drift is sign independent")
    void priceDriftIsSignIndependent() {
        BigDecimal internalPrice = new BigDecimal("100.00");

        boolean below = ReconciliationRule.PRICE_TOLERANCE_1PCT.matches(
                internalPrice, TEN_UNITS, new BigDecimal("99.50"), TEN_UNITS);
        boolean above = ReconciliationRule.PRICE_TOLERANCE_1PCT.matches(
                internalPrice, TEN_UNITS, new BigDecimal("100.50"), TEN_UNITS);

        assertThat(below).isTrue();
        assertThat(above).isTrue();
        assertThat(below).isEqualTo(above);
    }

    @Test
    @DisplayName("AC5: quantity drift is sign independent")
    void quantityDriftIsSignIndependent() {
        BigDecimal price = new BigDecimal("100.00");

        boolean below = ReconciliationRule.QTY_TOLERANCE_5UNITS.matches(
                price, TEN_UNITS, price, new BigDecimal("6"));
        boolean above = ReconciliationRule.QTY_TOLERANCE_5UNITS.matches(
                price, TEN_UNITS, price, new BigDecimal("14"));

        assertThat(below).isTrue();
        assertThat(above).isTrue();
        assertThat(below).isEqualTo(above);
    }

    @Test
    @DisplayName("AC5: sign independence also holds outside tolerance")
    void priceDriftIsSignIndependentWhenOutsideTolerance() {
        BigDecimal internalPrice = new BigDecimal("100.00");

        boolean below = ReconciliationRule.PRICE_TOLERANCE_1PCT.matches(
                internalPrice, TEN_UNITS, new BigDecimal("98.00"), TEN_UNITS);
        boolean above = ReconciliationRule.PRICE_TOLERANCE_1PCT.matches(
                internalPrice, TEN_UNITS, new BigDecimal("102.00"), TEN_UNITS);

        assertThat(below).isFalse();
        assertThat(above).isFalse();
    }

    // ------------------------------------------------------------------
    // AC6 — zero internal price must not blow up on division.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("AC6: a zero internal price never triggers ArithmeticException")
    void zeroInternalPriceDoesNotThrow() {
        assertThatCode(() -> ReconciliationRule.EXACT.matches(
                BigDecimal.ZERO, TEN_UNITS, BigDecimal.ZERO, TEN_UNITS))
                .doesNotThrowAnyException();

        assertThatCode(() -> ReconciliationRule.LOOSE.matches(
                BigDecimal.ZERO, TEN_UNITS, new BigDecimal("7.50"), TEN_UNITS))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("AC6: a zero internal price matches only against a zero external price")
    void zeroInternalPriceMatchesOnlyAnEqualPrice() {
        assertThat(ReconciliationRule.EXACT.matches(
                BigDecimal.ZERO, TEN_UNITS, BigDecimal.ZERO, TEN_UNITS)).isTrue();

        // Documented semantics: a percentage drift off a zero base is undefined,
        // so a non-zero external price can never be within a percentage tolerance.
        // Reporting it as a match would silently reconcile a real price break.
        assertThat(ReconciliationRule.EXACT.matches(
                BigDecimal.ZERO, TEN_UNITS, new BigDecimal("42.00"), TEN_UNITS)).isFalse();
        assertThat(ReconciliationRule.LOOSE.matches(
                BigDecimal.ZERO, TEN_UNITS, new BigDecimal("0.01"), TEN_UNITS)).isFalse();

        assertThat(ReconciliationRule.EXACT.matches(
                BigDecimal.ZERO, TEN_UNITS, BigDecimal.ZERO, new BigDecimal("11"))).isFalse();
    }

    // ------------------------------------------------------------------
    // AC7 — EXACT matches identical pairs only.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("AC7: EXACT matches an identical (price, quantity) pair")
    void exactMatchesIdenticalPair() {
        assertThat(ReconciliationRule.EXACT.matches(
                new BigDecimal("100.00"), TEN_UNITS,
                new BigDecimal("100.00"), TEN_UNITS)).isTrue();
    }

    @Test
    @DisplayName("AC7: EXACT rejects any non-zero price drift")
    void exactRejectsAnyNonZeroPriceDrift() {
        assertThat(ReconciliationRule.EXACT.matches(
                new BigDecimal("100.00"), TEN_UNITS,
                new BigDecimal("100.01"), TEN_UNITS)).isFalse();
        assertThat(ReconciliationRule.EXACT.matches(
                new BigDecimal("100.00"), TEN_UNITS,
                new BigDecimal("99.99"), TEN_UNITS)).isFalse();
    }

    @Test
    @DisplayName("AC7: EXACT rejects any non-zero quantity drift")
    void exactRejectsAnyNonZeroQuantityDrift() {
        BigDecimal price = new BigDecimal("100.00");

        assertThat(ReconciliationRule.EXACT.matches(
                price, TEN_UNITS, price, new BigDecimal("11"))).isFalse();
        assertThat(ReconciliationRule.EXACT.matches(
                price, TEN_UNITS, price, new BigDecimal("9"))).isFalse();
    }

    // ------------------------------------------------------------------
    // AC8 — BigDecimal comparison must use compareTo, not equals.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("AC8: EXACT is scale insensitive (compareTo, not equals)")
    void exactIsScaleInsensitive() {
        assertThat(ReconciliationRule.EXACT.matches(
                new BigDecimal("100.00"), BigDecimal.TEN,
                new BigDecimal("100.0"), BigDecimal.TEN)).isTrue();
    }

    @Test
    @DisplayName("AC8: scale insensitivity also applies to the quantity leg")
    void quantityComparisonIsScaleInsensitive() {
        assertThat(ReconciliationRule.EXACT.matches(
                new BigDecimal("100.00"), new BigDecimal("10.000"),
                new BigDecimal("100.00"), new BigDecimal("10"))).isTrue();
    }
}
