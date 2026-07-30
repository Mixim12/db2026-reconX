package com.dbtraining.reconx.service;

import com.dbtraining.reconx.dto.ReconResult;
import com.dbtraining.reconx.model.ReconciliationRule;
import com.dbtraining.reconx.model.TradeType;
import com.dbtraining.reconx.repository.ReconResultRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ReconciliationService {

    private final ReconciliationEngine engine;
    private final ReconResultRepository repository;

    public ReconciliationService(ReconciliationEngine engine, ReconResultRepository repository) {
        this.engine = engine;
        this.repository = repository;
    }

    public List<ReconResult> runRecon(List<TradeType> internal, List<TradeType> external) {
        return runRecon(internal, external, ReconciliationRule.EXACT);
    }

    public List<ReconResult> runRecon(List<TradeType> internal, List<TradeType> external, ReconciliationRule rule) {
        List<ReconResult> results = engine.reconcile(internal, external, rule);
        results.forEach(repository::save);
        return results;
    }
}
