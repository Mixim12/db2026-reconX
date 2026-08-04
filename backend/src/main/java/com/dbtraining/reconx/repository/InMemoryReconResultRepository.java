package com.dbtraining.reconx.repository;

import com.dbtraining.reconx.dto.ReconResult;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Repository
public class InMemoryReconResultRepository implements ReconResultRepository {

    private final List<ReconResult> store = Collections.synchronizedList(new ArrayList<>());

    @Override
    public ReconResult save(ReconResult result) {
        store.add(result);
        return result;
    }

    @Override
    public List<ReconResult> findAll() {
        return new ArrayList<>(store);
    }

    public void clear() {
        store.clear();
    }
}
