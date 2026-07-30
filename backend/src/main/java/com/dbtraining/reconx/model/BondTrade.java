package com.dbtraining.reconx.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Objects;

/**
 * ============================================================================
 * TICKET-ADV021 — BondTrade with Builder pattern
 *
 * WHAT:    Fixed-income trade — couponRate, maturityDate, faceValue, isin.
 * HOW:     Same builder pattern. notional() = faceValue (in the bond's ccy).
 * WHY:     Bonds need couponRate/maturity for downstream cashflow modelling.
 *          Modelling them on the trade is the simplest path for the demo.
 * ============================================================================
 */
public final class BondTrade extends Trade implements TradeType {

    private final String isin;
    private final BigDecimal faceValue;
    private final BigDecimal couponRate;
    private final LocalDate maturityDate;
    private final Currency currency;
    private final Side side;
    private final long counterpartyId;

    private BondTrade(Builder b) {
        super(
                b.tradeRef,
                new Money(b.faceValue, b.currency),
                b.tradeDate
        );

        this.isin = b.isin;
        this.faceValue = b.faceValue;
        this.couponRate = b.couponRate;
        this.maturityDate = b.maturityDate;
        this.currency = b.currency;
        this.side = b.side;
        this.counterpartyId = b.counterpartyId;
    }

    /** A new, empty {@link Builder} — the only way to obtain a {@code BondTrade}. */
    public static Builder builder() { return new Builder(); }

    @Override public AssetClass assetClass() { return AssetClass.BOND; }

    /** The 12-character ISIN identifying the bond. */
    public String isin()              { return isin; }
    /** The principal repaid at {@link #maturityDate()}, in {@link #currency()}. */
    public BigDecimal faceValue()     { return faceValue; }
    /** The annual coupon rate, expressed as a fraction (e.g. {@code 0.05} for 5%). */
    public BigDecimal couponRate()    { return couponRate; }
    /** The date the bond redeems; always strictly after {@link #tradeDate()}. */
    public LocalDate maturityDate()   { return maturityDate; }
    /** The currency {@link #faceValue()} and {@link #couponRate()} are denominated in. */
    public Currency currency()        { return currency; }
    /** Whether this trade is a BUY or a SELL. */
    public Side side()                { return side; }
    /** The internal id of the counterparty on the other side of the trade. */
    public long counterpartyId()      { return counterpartyId; }

    /**
     * Two {@code BondTrade}s are equal iff their {@link #tradeRef()} is equal.
     * @param o the object to compare against
     * @return {@code true} iff {@code o} is a {@code BondTrade} with the same {@code tradeRef}
     */
    @Override
    public boolean equals(Object o) {
        return this == o
                || (o instanceof BondTrade other
                && tradeRef().equals(other.tradeRef()));
    }

    /** {@code tradeRef.hashCode()}, kept in lockstep with {@link #equals(Object)}. */
    @Override
    public int hashCode() {
        return tradeRef().hashCode();
    }

    /** A PII-safe log representation — omits {@link #counterpartyId()}. */
    @Override
    public String toString() {
        // NOTE: counterpartyId and any settlement or issuer identifiers are
        // deliberately omitted to prevent sensitive data from reaching logs.
        return "BondTrade[ref=%s, isin=%s, face=%s %s, coupon=%s, maturity=%s, side=%s]"
                .formatted(
                        tradeRef().value(),
                        isin,
                        faceValue.toPlainString(),
                        currency.getCurrencyCode(),
                        couponRate.toPlainString(),
                        maturityDate,
                        side
                );
    }

    public static final class Builder {
        private TradeRef tradeRef;
        private String isin;
        private BigDecimal faceValue, couponRate;
        private LocalDate maturityDate, tradeDate;
        private Currency currency;
        private Side side;
        private long counterpartyId;

        public Builder tradeRef(TradeRef v)        { this.tradeRef = v; return this; }
        public Builder isin(String v)              { this.isin = v; return this; }
        public Builder faceValue(BigDecimal v)     { this.faceValue = v; return this; }
        public Builder couponRate(BigDecimal v)    { this.couponRate = v; return this; }
        public Builder maturityDate(LocalDate v)   { this.maturityDate = v; return this; }
        public Builder currency(String code)       { this.currency = Currency.getInstance(code); return this; }
        public Builder side(Side v)                { this.side = v; return this; }
        public Builder tradeDate(LocalDate v)      { this.tradeDate = v; return this; }
        public Builder counterpartyId(long v)      { this.counterpartyId = v; return this; }

        /**
         * Build the immutable {@link BondTrade}, validating that every required
         * field is set and that all invariants hold.
         *
         * @return a fully-constructed, validated {@code BondTrade} — never {@code null}.
         * @throws NullPointerException  if any required field ({@code tradeRef}, {@code isin},
         *                               {@code faceValue}, {@code couponRate}, {@code maturityDate},
         *                               {@code currency}, {@code side}, {@code tradeDate}) was not set.
         * @throws IllegalStateException if {@code maturityDate} is not strictly after
         *                               {@code tradeDate}.
         */
        public BondTrade build() {
            Objects.requireNonNull(tradeRef,     "tradeRef");
            Objects.requireNonNull(isin,         "isin");
            Objects.requireNonNull(faceValue,    "faceValue");
            Objects.requireNonNull(couponRate,   "couponRate");
            Objects.requireNonNull(maturityDate, "maturityDate");
            Objects.requireNonNull(currency,     "currency");
            Objects.requireNonNull(side,         "side");
            Objects.requireNonNull(tradeDate,    "tradeDate");
            if (isin.length() != 12) throw new IllegalStateException("isin must be 12 characters");
            if (faceValue.signum() <= 0) throw new IllegalStateException("faceValue must be > 0");
            if (couponRate.signum() < 0) throw new IllegalStateException("couponRate cannot be negative");
            if (!maturityDate.isAfter(tradeDate))
                throw new IllegalStateException("maturityDate cannot be before tradeDate");
            return new BondTrade(this);
        }
    }
}
