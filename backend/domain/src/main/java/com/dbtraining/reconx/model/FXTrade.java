package com.dbtraining.reconx.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Objects;

/**
 * FX trade containing a base currency, a quote currency and an FX rate.
 *
 * The original notional is stored in {@code ccy1} as {@code notionalCcy1}.
 * The value returned by {@link #notional()} is converted into {@code ccy2}:
 *
 * notionalCcy1 * fxRate
 */
public final class FXTrade extends Trade implements TradeType {

    private final Currency ccy1;
    private final Currency ccy2;
    private final BigDecimal notionalCcy1;
    private final BigDecimal fxRate;
    private final Side side;
    private final long counterpartyId;

    private FXTrade(Builder builder) {
        super(
                builder.tradeRef,
                new Money(
                        builder.notionalCcy1.multiply(builder.fxRate),
                        builder.ccy2
                ),
                builder.tradeDate
        );

        this.ccy1 = builder.ccy1;
        this.ccy2 = builder.ccy2;
        this.notionalCcy1 = builder.notionalCcy1;
        this.fxRate = builder.fxRate;
        this.side = builder.side;
        this.counterpartyId = builder.counterpartyId;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public AssetClass assetClass() {
        return AssetClass.FX;
    }

    public Currency ccy1() {
        return ccy1;
    }

    public Currency ccy2() {
        return ccy2;
    }

    public BigDecimal notionalCcy1() {
        return notionalCcy1;
    }

    public BigDecimal fxRate() {
        return fxRate;
    }

    public Side side() {
        return side;
    }

    public long counterpartyId() {
        return counterpartyId;
    }

    @Override
    public boolean equals(Object o) {
        return this == o
                || (o instanceof FXTrade other
                && tradeRef().equals(other.tradeRef()));
    }

    @Override
    public int hashCode() {
        return tradeRef().hashCode();
    }

    @Override
    public String toString() {
        // NOTE: counterpartyId and the computed ccy2 settlement notional are
        // deliberately omitted to prevent sensitive data from reaching logs.
        return "FXTrade[ref=%s, %s/%s, notional=%s %s, rate=%s, side=%s]"
                .formatted(
                        tradeRef().value(),
                        ccy1.getCurrencyCode(),
                        ccy2.getCurrencyCode(),
                        notionalCcy1.toPlainString(),
                        ccy1.getCurrencyCode(),
                        fxRate.toPlainString(),
                        side
                );
    }

    public static final class Builder {

        private TradeRef tradeRef;
        private Currency ccy1;
        private Currency ccy2;
        private BigDecimal notionalCcy1;
        private BigDecimal fxRate;
        private Side side;
        private LocalDate tradeDate;
        private long counterpartyId;

        private Builder() {
        }

        public Builder tradeRef(TradeRef tradeRef) {
            this.tradeRef = tradeRef;
            return this;
        }

        public Builder ccy1(Currency currency) {
            this.ccy1 = currency;
            return this;
        }

        public Builder ccy1(String code) {
            return ccy1(Currency.getInstance(code));
        }

        public Builder ccy2(Currency currency) {
            this.ccy2 = currency;
            return this;
        }

        public Builder ccy2(String code) {
            return ccy2(Currency.getInstance(code));
        }

        public Builder notionalCcy1(BigDecimal notionalCcy1) {
            this.notionalCcy1 = notionalCcy1;
            return this;
        }

        public Builder fxRate(BigDecimal fxRate) {
            this.fxRate = fxRate;
            return this;
        }

        public Builder side(Side side) {
            this.side = side;
            return this;
        }

        public Builder tradeDate(LocalDate tradeDate) {
            this.tradeDate = tradeDate;
            return this;
        }

        public Builder counterpartyId(long counterpartyId) {
            this.counterpartyId = counterpartyId;
            return this;
        }

        public FXTrade build() {
            Objects.requireNonNull(tradeRef, "tradeRef");
            Objects.requireNonNull(ccy1, "ccy1");
            Objects.requireNonNull(ccy2, "ccy2");
            Objects.requireNonNull(notionalCcy1, "notionalCcy1");
            Objects.requireNonNull(fxRate, "fxRate");
            Objects.requireNonNull(side, "side");
            Objects.requireNonNull(tradeDate, "tradeDate");

            if (ccy1.equals(ccy2)) {
                throw new IllegalStateException(
                        "ccy1 and ccy2 must differ"
                );
            }

            if (notionalCcy1.signum() <= 0) {
                throw new IllegalStateException(
                        "notionalCcy1 must be > 0"
                );
            }

            if (fxRate.signum() <= 0) {
                throw new IllegalStateException(
                        "fxRate must be > 0"
                );
            }

            return new FXTrade(this);
        }
    }
}