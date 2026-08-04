package com.dbtraining.reconx.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * ============================================================================
 * TICKET-ADV026 — ReconciliationRule enum with configurable thresholds
 *
 * WHAT:    Each enum value carries its own price tolerance (%) and quantity
 *          tolerance (absolute units). {@link #matches} returns true if the
 *          internal vs external trade pair is within tolerance.
 * HOW:     Enum-with-state pattern — instance fields + a behaviour method.
 * WHY:     Putting the rule on the enum keeps "what is a match" co-located
 *          with the rule's name, so the reconciliation engine is just:
 *          `if (rule.matches(internal, external)) ... matched ...`.
 * OBSERVE: PRICE_TOLERANCE_1PCT.matches(p, p*1.005) is true; *1.02 is false.
 * ============================================================================
 */
public enum ReconciliationRule {

    EXACT(BigDecimal.ZERO, BigDecimal.ZERO),
    PRICE_TOLERANCE_1PCT(new BigDecimal("0.01"), BigDecimal.ZERO),
    PRICE_TOLERANCE_50BPS(new BigDecimal("0.005"), BigDecimal.ZERO),
    QTY_TOLERANCE_5UNITS(BigDecimal.ZERO, new BigDecimal("5")),
    LOOSE(new BigDecimal("0.05"), new BigDecimal("10"));

    private final BigDecimal priceTolerancePct;
    private final BigDecimal qtyToleranceAbs;

    ReconciliationRule(BigDecimal priceTolerancePct, BigDecimal qtyToleranceAbs) {
        this.priceTolerancePct = priceTolerancePct;
        this.qtyToleranceAbs   = qtyToleranceAbs;
    }

    /** The maximum allowed price drift, as a fraction (e.g. {@code 0.01} for 1%). */
    public BigDecimal priceTolerancePct() { return priceTolerancePct; }
    /** The maximum allowed absolute quantity drift. */
    public BigDecimal qtyToleranceAbs()   { return qtyToleranceAbs; }

    /**
     * Scale used when dividing the absolute price difference by the internal
     * price. Ten decimal places is far finer than any tolerance we model, so
     * the rounding never decides a borderline case on its own.
     */
    private static final int DRIFT_SCALE = 10;

    /**
     * Decide whether two prices/quantities are within this rule's tolerance.
     *
     * <p>Price drift is expressed as a fraction of the internal price
     * (so {@code 0.01} means 1%), quantity drift as an absolute unit count.
     * Both drifts are absolute values, which makes the comparison
     * sign-independent: an external price above or below the internal one by
     * the same amount yields the same answer.
     *
     * <p>Zero-price guard: when {@code internalPrice} is zero there is no
     * meaningful denominator, so no division is attempted and no
     * {@link ArithmeticException} is raised. Equal prices are always within
     * tolerance, so a zero/zero pair still passes its price leg; a zero
     * internal price against a non-zero external price never does. Calling
     * that drift "zero" would silently reconcile a real price break.
     *
     * <p>All numeric comparisons go through {@link BigDecimal#compareTo},
     * never {@code equals}, so {@code 100.00} and {@code 100.0} are treated
     * as the same value despite their different scales.
     *
     * @param internalPrice the price on the internal (book of record) side
     * @param internalQty   the quantity on the internal side
     * @param externalPrice the price on the external (counterparty/custodian) side
     * @param externalQty   the quantity on the external side
     * @return true if BOTH the price diff (as %) AND the qty diff (as abs)
     *         are within tolerance.
     */
    public boolean matches(BigDecimal internalPrice, BigDecimal internalQty,
                           BigDecimal externalPrice, BigDecimal externalQty) {
        BigDecimal qtyDrift = externalQty.subtract(internalQty).abs();

        return priceWithinTolerance(internalPrice, externalPrice)
                && qtyDrift.compareTo(qtyToleranceAbs) <= 0;
    }

    /**
     * Whether the price leg is within this rule's percentage tolerance.
     * Identical prices always pass; a non-zero difference off a zero internal
     * price never does, because the percentage is undefined there.
     */
    private boolean priceWithinTolerance(BigDecimal internalPrice, BigDecimal externalPrice) {
        BigDecimal priceDiff = externalPrice.subtract(internalPrice).abs();
        if (priceDiff.signum() == 0) {
            return true;
        }
        if (internalPrice.signum() == 0) {
            return false;
        }
        BigDecimal priceDrift = priceDiff.divide(internalPrice.abs(), DRIFT_SCALE, RoundingMode.HALF_UP);
        return priceDrift.compareTo(priceTolerancePct) <= 0;
    }
}
