package com.dbtraining.reconx.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Objects;

/**
 * ============================================================================
 * TICKET-ADV022 — DerivativeTrade with Builder pattern
 *
 * WHAT:    Option/derivative trade with underlying, strike, quantity,
 *          expiry and option type.
 * HOW:     Construction happens through a Builder. The simplified notional
 *          is strike * quantity in the trade currency.
 *
 * Historical derivatives are valid reconciliation records. Expiry is
 * validated only against tradeDate and is deliberately not compared with
 * LocalDate.now().
 * ============================================================================
 */
public final class DerivativeTrade extends Trade implements TradeType {

    public enum OptionType {
        CALL,
        PUT
    }

    private final String underlying;
    private final BigDecimal strike;
    private final BigDecimal quantity;
    private final LocalDate expiry;
    private final OptionType optionType;
    private final Currency currency;
    private final Side side;
    private final long counterpartyId;

    private DerivativeTrade(Builder builder) {
        super(
                builder.tradeRef,
                new Money(
                        builder.strike.multiply(builder.quantity),
                        builder.currency
                ),
                builder.tradeDate
        );

        this.underlying = builder.underlying;
        this.strike = builder.strike;
        this.quantity = builder.quantity;
        this.expiry = builder.expiry;
        this.optionType = builder.optionType;
        this.currency = builder.currency;
        this.side = builder.side;
        this.counterpartyId = builder.counterpartyId;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public AssetClass assetClass() {
        return AssetClass.DERIVATIVE;
    }

    public String underlying() {
        return underlying;
    }

    public BigDecimal strike() {
        return strike;
    }

    public BigDecimal quantity() {
        return quantity;
    }

    public LocalDate expiry() {
        return expiry;
    }

    public OptionType optionType() {
        return optionType;
    }

    public Currency currency() {
        return currency;
    }

    public Side side() {
        return side;
    }

    public long counterpartyId() {
        return counterpartyId;
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || (other instanceof DerivativeTrade derivative
                && tradeRef().equals(derivative.tradeRef()));
    }

    @Override
    public int hashCode() {
        return tradeRef().hashCode();
    }

    @Override
    public String toString() {
        // NOTE: counterpartyId and computed settlement notional are deliberately
        // omitted to prevent PII and sensitive settlement data from reaching logs.
        return "DerivativeTrade[ref=%s, %s %s on %s, strike=%s %s, qty=%s, expiry=%s, side=%s]"
                .formatted(
                        tradeRef().value(),
                        optionType,
                        underlying,
                        tradeDate(),
                        strike.toPlainString(),
                        currency.getCurrencyCode(),
                        quantity.toPlainString(),
                        expiry,
                        side
                );
    }

    public static final class Builder {

        private TradeRef tradeRef;
        private String underlying;
        private BigDecimal strike;
        private BigDecimal quantity;
        private LocalDate expiry;
        private OptionType optionType;
        private Currency currency;
        private Side side;
        private LocalDate tradeDate;
        private long counterpartyId;

        private Builder() {
        }

        public Builder tradeRef(TradeRef tradeRef) {
            this.tradeRef = tradeRef;
            return this;
        }

        public Builder underlying(String underlying) {
            this.underlying = underlying;
            return this;
        }

        public Builder strike(BigDecimal strike) {
            this.strike = strike;
            return this;
        }

        public Builder quantity(BigDecimal quantity) {
            this.quantity = quantity;
            return this;
        }

        public Builder expiry(LocalDate expiry) {
            this.expiry = expiry;
            return this;
        }

        public Builder optionType(OptionType optionType) {
            this.optionType = optionType;
            return this;
        }

        public Builder currency(String code) {
            this.currency = Currency.getInstance(code);
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

        public DerivativeTrade build() {
            Objects.requireNonNull(tradeRef, "tradeRef");
            Objects.requireNonNull(underlying, "underlying");
            Objects.requireNonNull(strike, "strike");
            Objects.requireNonNull(quantity, "quantity");
            Objects.requireNonNull(expiry, "expiry");
            Objects.requireNonNull(optionType, "optionType");
            Objects.requireNonNull(currency, "currency");
            Objects.requireNonNull(side, "side");
            Objects.requireNonNull(tradeDate, "tradeDate");

            if (strike.signum() <= 0) {
                throw new IllegalStateException("strike must be > 0");
            }

            if (quantity.signum() <= 0) {
                throw new IllegalStateException("quantity must be > 0");
            }

            // Expiry must be strictly after tradeDate.
            // No comparison with LocalDate.now(): expired historical trades are valid.
            if (!expiry.isAfter(tradeDate)) {
                throw new IllegalStateException(
                        "expiry cannot be before tradeDate"
                );
            }

            return new DerivativeTrade(this);
        }
    }
}