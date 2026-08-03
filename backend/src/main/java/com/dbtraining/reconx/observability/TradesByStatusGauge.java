package com.dbtraining.reconx.observability;

import com.dbtraining.reconx.repository.TradeRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * TICKET-ADV092 — Grafana pie chart: trades by status
 *
 * Exposes a 'trades_by_status' gauge metric with a 'status' tag for each status:
 * PENDING, MATCHED, UNMATCHED, DISPUTED, CANCELLED.
 */
@Component
public class TradesByStatusGauge {

    public TradesByStatusGauge(MeterRegistry registry, TradeRepository repo) {
        for (String status : List.of("PENDING", "MATCHED", "UNMATCHED", "DISPUTED", "CANCELLED")) {
            Gauge.builder("trades_by_status", repo, r -> r.countByStatus(status))
                    .tag("status", status)
                    .description("Trades currently in a given status")
                    .register(registry);
        }
    }
}
