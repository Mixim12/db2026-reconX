package com.dbtraining.reconx.dto;

import com.dbtraining.reconx.repository.entity.ReconBreak;

import java.time.Instant;

/**
 * TICKET-ADV069 — response shape for GET /v1/recon/jobs/{jobId}/results.
 * Keeps the JPA entity (ReconBreak) from leaking directly onto the wire.
 */
public record ReconResultResponse(
        Long id,
        Long tradeId,
        String jobId,
        String discrepancyType,
        String status,
        Instant detectedAt,
        Instant resolvedAt,
        String resolutionNote
) {
    public static ReconResultResponse from(ReconBreak b) {
        return new ReconResultResponse(
                b.getId(), b.getTradeId(), b.getJobId(), b.getDiscrepancyType(),
                b.getStatus(), b.getDetectedAt(), b.getResolvedAt(), b.getResolutionNote());
    }
}
