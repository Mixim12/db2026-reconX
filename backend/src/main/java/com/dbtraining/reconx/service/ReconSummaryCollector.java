package com.dbtraining.reconx.service;

import com.dbtraining.reconx.dto.ReconResult;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

/**
 * ============================================================================
 * TICKET-ADV038 — custom Collector reducing recon results to a ReconSummary
 *
 * WHAT:    Collector&lt;ReconResult, ReconSummary.Builder, ReconSummary&gt;
 *          counting total rows plus the MATCHED/BREAK split.
 * HOW:     Same four-contribution skeleton as ADV035's {@link VwapCollector},
 *          generalised from a BigDecimal to a domain object. The combiner
 *          returns a *fresh* Builder holding the field-wise sum rather than
 *          mutating either input — that is what makes it associative and
 *          safe under parallel splits.
 * WHY:     Counting by hand in a loop, or via three separate stream passes,
 *          both lose the single-traversal property; a Collector keeps the
 *          roll-up composable as a downstream of groupingBy (ADV034/ADV036).
 * OBSERVE: Collecting 10k results serially and via parallelStream yields an
 *          identical ReconSummary. No IDENTITY_FINISH (the finisher builds a
 *          different type) and no CONCURRENT (the Builder is not thread-safe).
 * ============================================================================
 */
public final class ReconSummaryCollector
        implements Collector<ReconResult, ReconSummary.Builder, ReconSummary> {

    @Override
    public Supplier<ReconSummary.Builder> supplier() {
        return ReconSummary.Builder::new;
    }

    @Override
    public BiConsumer<ReconSummary.Builder, ReconResult> accumulator() {
        return (builder, result) -> {
            builder.total++;
            if (result.status() == ReconResult.Status.MATCHED) {
                builder.matched++;
            } else {
                builder.broken++;
            }
        };
    }

    @Override
    public BinaryOperator<ReconSummary.Builder> combiner() {
        return (left, right) -> {
            ReconSummary.Builder merged = new ReconSummary.Builder();
            merged.total = left.total + right.total;
            merged.matched = left.matched + right.matched;
            merged.broken = left.broken + right.broken;
            return merged;
        };
    }

    @Override
    public Function<ReconSummary.Builder, ReconSummary> finisher() {
        return builder -> new ReconSummary(builder.total, builder.matched, builder.broken);
    }

    @Override
    public Set<Characteristics> characteristics() {
        return EnumSet.of(Characteristics.UNORDERED);
    }
}
