package com.dbtraining.reconx.service;

import com.dbtraining.reconx.model.EquityTrade;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

/**
 * ============================================================================
 * TICKET-ADV035 — custom Collector computing the volume-weighted average price
 *
 * WHAT:    Collector&lt;EquityTrade, ?, BigDecimal&gt; reducing a stream of
 *          equity trades to VWAP = SUM(price * qty) / SUM(qty).
 * HOW:     A tiny mutable accumulator carries two running BigDecimal totals.
 *          The combiner returns a *fresh* accumulator summing both inputs'
 *          fields rather than mutating either input - required for
 *          serial/parallel result parity.
 * WHY:     Writing all four Collector contributions by hand (instead of
 *          reaching for groupingBy/toMap) is the point of this ticket, and
 *          is the skeleton ADV038's ReconSummaryCollector will reuse.
 * OBSERVE: A serial stream and a parallelStream over the same input produce
 *          identical BigDecimal results; empty input yields BigDecimal.ZERO.
 *
 * NOTE: the guide's "Done when" list asks for 6 decimal places, but its own
 * Hint 4 reference solution and "Observe" verification text both use 4
 * decimal places - an internal inconsistency in the guide, not in this code.
 * Resolved as 4 decimal places (matching the reference solution and the
 * verification text, i.e. 2 of the guide's 3 mentions).
 * ============================================================================
 */
public final class VwapCollector implements Collector<EquityTrade, VwapCollector.Accumulator, BigDecimal> {

    private static final int SCALE = 4;

    @Override
    public Supplier<Accumulator> supplier() {
        return Accumulator::new;
    }

    @Override
    public BiConsumer<Accumulator, EquityTrade> accumulator() {
        return (acc, trade) -> {
            acc.sumPriceQty = acc.sumPriceQty.add(trade.price().multiply(trade.quantity()));
            acc.sumQty = acc.sumQty.add(trade.quantity());
        };
    }

    @Override
    public BinaryOperator<Accumulator> combiner() {
        return (a, b) -> {
            Accumulator out = new Accumulator();
            out.sumPriceQty = a.sumPriceQty.add(b.sumPriceQty);
            out.sumQty = a.sumQty.add(b.sumQty);
            return out;
        };
    }

    @Override
    public Function<Accumulator, BigDecimal> finisher() {
        return acc -> acc.sumQty.signum() == 0
                ? BigDecimal.ZERO
                : acc.sumPriceQty.divide(acc.sumQty, SCALE, RoundingMode.HALF_UP);
    }

    @Override
    public Set<Characteristics> characteristics() {
        return EnumSet.of(Characteristics.UNORDERED);
    }

    static final class Accumulator {
        BigDecimal sumPriceQty = BigDecimal.ZERO;
        BigDecimal sumQty = BigDecimal.ZERO;
    }
}
