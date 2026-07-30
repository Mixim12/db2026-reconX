package com.dbtraining.reconx.service;

/**
 * ============================================================================
 * TICKET-ADV038 — immutable roll-up of a reconciliation run
 *
 * WHAT:    Three counts describing a Stream&lt;ReconResult&gt;: how many rows
 *          were seen, how many matched, how many broke.
 * HOW:     A record, so the counts cannot drift after construction. The
 *          nested {@link Builder} is the mutable accumulator type used while
 *          a collect is in flight; it never escapes the collector.
 * WHY:     ADV047's all-mismatched edge-case test asserts against this shape
 *          ({@code summary.matched() == 0}, {@code summary.broken() == 3}),
 *          so the counts have to be a domain object rather than a Map.
 * OBSERVE: {@code empty()} returns all zeros; a collected summary always
 *          satisfies {@code total == matched + broken}.
 * ============================================================================
 */
public record ReconSummary(long total, long matched, long broken) {

    /** Neutral summary — the identity for combining, and the empty-stream result. */
    public static ReconSummary empty() {
        return new ReconSummary(0L, 0L, 0L);
    }

    /**
     * Mutable accumulator for {@link ReconSummaryCollector}. Package-visible
     * fields keep the accumulator/combiner hot path free of getter noise; the
     * instance is confined to a single collect and is discarded by the finisher.
     */
    public static final class Builder {
        long total;
        long matched;
        long broken;
    }
}
