package com.dbtraining.reconx.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/**
 * ============================================================================
 * TICKET-ADV084 — Timer: reconciliation_duration_seconds
 *
 * WHAT:    Micrometer Timer tracking reconciliation engine duration.
 * HOW:     Configured with publishPercentileHistogram() to emit _bucket
 *          series required by PromQL histogram_quantile() queries.
 * WHY:     Enables server-side calculation of P95/P99 latency metrics
 *          and heatmap visualizations in Grafana.
 * ============================================================================
 */
@Component
public class ReconMetrics {

    private final Timer reconciliationTimer;

    public ReconMetrics(MeterRegistry registry) {
        this.reconciliationTimer = Timer.builder("reconciliation_duration_seconds")
                .description("Reconciliation engine batch execution duration in seconds")
                .publishPercentileHistogram()
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
    }

    public Timer reconciliationTimer() {
        return reconciliationTimer;
    }
}
