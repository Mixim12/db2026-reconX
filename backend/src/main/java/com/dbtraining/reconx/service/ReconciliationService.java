package com.dbtraining.reconx.service;

import com.dbtraining.reconx.dto.ReconResult;
import com.dbtraining.reconx.model.ReconciliationRule;
import com.dbtraining.reconx.model.TradeType;
import com.dbtraining.reconx.observability.ReconMetrics;
import com.dbtraining.reconx.repository.ReconResultRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ReconciliationService {

    private final ReconciliationEngine engine;
    private final ReconResultRepository repository;
    private final ReconMetrics reconMetrics;

    public ReconciliationService(ReconciliationEngine engine, ReconResultRepository repository) {
        this(engine, repository, new ReconMetrics(new SimpleMeterRegistry()));
    }

    public ReconciliationService(ReconciliationEngine engine, ReconResultRepository repository, ReconMetrics reconMetrics) {
        this.engine = engine;
        this.repository = repository;
        this.reconMetrics = reconMetrics;
    }

    public List<ReconResult> runRecon(List<TradeType> internal, List<TradeType> external) {
        return runRecon(internal, external, ReconciliationRule.EXACT);
    }

    public List<ReconResult> runRecon(List<TradeType> internal, List<TradeType> external, ReconciliationRule rule) {
        List<ReconResult> results = reconMetrics.reconciliationTimer()
                .record(() -> engine.reconcile(internal, external, rule));
        results.forEach(repository::save);
        return results;
    }
}
