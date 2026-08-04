package com.dbtraining.reconx.observability;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReconMetricsTest {

    @Test
    void testReconMetrics_registersReconciliationTimer() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ReconMetrics metrics = new ReconMetrics(registry);

        Timer timer = metrics.reconciliationTimer();
        assertThat(timer).isNotNull();
        assertThat(timer.getId().getName()).isEqualTo("reconciliation_duration_seconds");

        String result = timer.record(() -> "reconciled");
        assertThat(result).isEqualTo("reconciled");
        assertThat(timer.count()).isEqualTo(1);
    }
}
