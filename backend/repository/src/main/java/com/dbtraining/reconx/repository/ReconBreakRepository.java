package com.dbtraining.reconx.repository;

import com.dbtraining.reconx.repository.entity.ReconBreak;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReconBreakRepository extends JpaRepository<ReconBreak, Long> {
    /** TICKET-ADV085 — exported as recon_break_count gauge. */
    long countByStatus(String status);

    /** TICKET-ADV069 — paginated breaks for a single reconciliation job. */
    Page<ReconBreak> findByJobId(String jobId, Pageable pageable);
}
