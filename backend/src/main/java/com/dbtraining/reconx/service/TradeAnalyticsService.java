package com.dbtraining.reconx.service;

import com.dbtraining.reconx.model.BondTrade;
import com.dbtraining.reconx.model.DerivativeTrade;
import com.dbtraining.reconx.model.EquityTrade;
import com.dbtraining.reconx.model.FXTrade;
import com.dbtraining.reconx.model.TradeType;
import com.dbtraining.reconx.model.Side;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collector;
import java.util.stream.Collectors;


/**
 * ============================================================================
 * TICKET-ADV034 — Trade analytics with Collectors (groupingBy + summarizing)
 * TICKET-ADV035 — VWAP calculator using Streams + custom collector
 * TICKET-ADV036 — P&L per instrument: stream reduction
 * ============================================================================
 */
@Service
public class TradeAnalyticsService {

    /**
     * TICKET-ADV034
     *
     * Computes count, total, minimum, maximum and average notional
     * for every counterparty.
     *
     * Every trade is sent directly to its counterparty accumulator.
     * There is no intermediate list and no second stream over each group.
     */
    public Map<Long, NotionalSummary> notionalByCounterparty(
            List<? extends TradeType> trades) {

        Objects.requireNonNull(trades, "trades must not be null");

        return trades.stream()
                .collect(Collectors.groupingBy(
                        this::counterpartyIdOf,
                        notionalSummaryCollector()
                ));
    }

    /**
     * Downstream collector used by groupingBy.
     *
     * The mutable accumulator stores the five aggregate values while
     * the stream is being processed.
     */
    private static Collector<
            TradeType,
            NotionalAccumulator,
            NotionalSummary> notionalSummaryCollector() {

        return Collector.of(
                NotionalAccumulator::new,
                NotionalAccumulator::add,
                NotionalAccumulator::combine,
                NotionalAccumulator::finish
        );
    }

    /**
     * TICKET-ADV035 — VWAP = SUM(price * qty) / SUM(qty).
     */
    public Map<String, BigDecimal> vwapByInstrument(
            List<EquityTrade> equityTrades) {

        return equityTrades.stream()
                .collect(Collectors.groupingBy(
                        EquityTrade::instrumentSymbol,
                        new VwapCollector()
                ));
    }

    /**
     * TICKET-ADV036 — P&L per instrument symbol.
     */
    public Map<String, BigDecimal> pnlByInstrument(
            List<EquityTrade> equityTrades) {

        Objects.requireNonNull(
                equityTrades,
                "equityTrades must not be null"
        );
        return equityTrades.stream()
                .collect(Collectors.groupingBy(
                        EquityTrade::instrumentSymbol,
                        Collectors.mapping(
                                this::pnl,
                                Collectors.reducing(
                                        BigDecimal.ZERO,
                                        BigDecimal::add
                                )
                        )
                ));
    }

    private BigDecimal pnl(EquityTrade trade) {
        Objects.requireNonNull(trade,
                "trade must not be null");

        BigDecimal absoluteValue = trade.price()
                .multiply(trade.quantity());

        return trade.side() == Side.SELL
                ? absoluteValue
                : absoluteValue.negate();
    }

    /**
     * Exhaustive switch enabled by the sealed TradeType hierarchy.
     */
    private long counterpartyIdOf(TradeType trade) {
        Objects.requireNonNull(trade, "trade must not be null");

        return switch (trade) {
            case EquityTrade equityTrade ->
                    equityTrade.counterpartyId();

            case FXTrade fxTrade ->
                    fxTrade.counterpartyId();

            case BondTrade bondTrade ->
                    bondTrade.counterpartyId();

            case DerivativeTrade derivativeTrade ->
                    derivativeTrade.counterpartyId();
        };
    }

    /**
     * Immutable result containing all five aggregates required by ADV034.
     */
    public record NotionalSummary(
            long count,
            BigDecimal total,
            BigDecimal min,
            BigDecimal max,
            BigDecimal average) {

        public NotionalSummary {
            if (count <= 0) {
                throw new IllegalArgumentException(
                        "count must be greater than zero"
                );
            }

            Objects.requireNonNull(total, "total must not be null");
            Objects.requireNonNull(min, "min must not be null");
            Objects.requireNonNull(max, "max must not be null");
            Objects.requireNonNull(average, "average must not be null");
        }
    }

    /**
     * Mutable accumulation type used internally by the collector.
     *
     * It is never exposed outside TradeAnalyticsService.
     */
    private static final class NotionalAccumulator {

        private long count;
        private BigDecimal total = BigDecimal.ZERO;
        private BigDecimal min;
        private BigDecimal max;

        /**
         * Processes exactly one trade.
         */
        private void add(TradeType trade) {
            Objects.requireNonNull(trade, "trade must not be null");

            BigDecimal amount = Objects.requireNonNull(
                    trade.notional().amount(),
                    "notional amount must not be null"
            );

            count++;
            total = total.add(amount);

            if (min == null || amount.compareTo(min) < 0) {
                min = amount;
            }

            if (max == null || amount.compareTo(max) > 0) {
                max = amount;
            }
        }

        /**
         * Required by Collector for correct parallel-stream behaviour.
         */
        private NotionalAccumulator combine(
                NotionalAccumulator other) {

            Objects.requireNonNull(other, "other must not be null");

            count += other.count;
            total = total.add(other.total);

            if (other.min != null &&
                    (min == null || other.min.compareTo(min) < 0)) {
                min = other.min;
            }

            if (other.max != null &&
                    (max == null || other.max.compareTo(max) > 0)) {
                max = other.max;
            }

            return this;
        }

        /**
         * Converts the internal mutable state to the immutable record.
         */
        private NotionalSummary finish() {
            if (count == 0) {
                throw new IllegalStateException(
                        "Cannot create a summary for an empty group"
                );
            }

            BigDecimal average = total.divide(
                    BigDecimal.valueOf(count),
                    MathContext.DECIMAL128
            );

            return new NotionalSummary(
                    count,
                    total,
                    min,
                    max,
                    average
            );
        }
    }
}