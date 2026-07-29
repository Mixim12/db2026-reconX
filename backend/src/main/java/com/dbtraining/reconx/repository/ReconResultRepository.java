package com.dbtraining.reconx.repository;

import com.dbtraining.reconx.dto.ReconResult;

import java.util.List;

public interface ReconResultRepository {
    ReconResult save(ReconResult result);
    List<ReconResult> findAll();
}
