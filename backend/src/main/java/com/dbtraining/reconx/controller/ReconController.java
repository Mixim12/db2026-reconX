package com.dbtraining.reconx.controller;

import com.dbtraining.reconx.dto.PagedResponse;
import com.dbtraining.reconx.dto.ReconResultResponse;
import com.dbtraining.reconx.dto.ReconRunRequest;
import com.dbtraining.reconx.exception.TradeNotFoundException;
import com.dbtraining.reconx.repository.ReconBreakRepository;
import com.dbtraining.reconx.repository.entity.ReconBreak;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.transaction.annotation.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

import java.util.Map;
import java.util.UUID;

/**
 * TICKET-ADV068 — POST /api/v1/recon/run — returns 202 + jobId
 * TICKET-ADV069 — GET  /api/v1/recon/jobs/{jobId}/results
 * TICKET-ADV070 — PUT  /api/v1/recon/results/{id}/resolve
 */
@RestController
@RequestMapping("/v1/recon")
@Tag(name = "recon", description = "Reconciliation operations")
@SecurityRequirement(name = "bearerAuth")
public class ReconController {

    private final ReconBreakRepository breaks;

    public ReconController(ReconBreakRepository breaks) { this.breaks = breaks; }

    private static final Logger log =
            LoggerFactory.getLogger(ReconController.class);

    public record ResolutionRequest(
            @NotBlank(message = "note must not be blank")
            @Size(max = 500, message = "note must not exceed 500 characters")
            String note
    ) {}

    @PostMapping("/run")
    @Operation(summary = "Trigger a reconciliation job (async)")
    public ResponseEntity<Map<String, String>> runRecon(
            @Valid @RequestBody ReconRunRequest req) {

        String jobId = UUID.randomUUID().toString();

        log.info(
                "recon job dispatched: jobId={}, from={}, to={}, counterpartyId={}",
                jobId,
                req.from(),
                req.to(),
                req.counterpartyId()
        );

        URI location = URI.create(
                "/api/v1/recon/jobs/" + jobId + "/results"
        );

        Map<String, String> response = Map.of(
                "jobId", jobId,
                "status", "QUEUED"
        );

        return ResponseEntity
                .accepted()
                .location(location)
                .body(response);
    }

    @GetMapping("/jobs/{jobId}/results")
    @Operation(summary = "Get results for a recon job")
    public PagedResponse<ReconResultResponse> results(
            @PathVariable String jobId,
            @PageableDefault(size = 50) Pageable pageable) {
        return PagedResponse.from(breaks.findByJobId(jobId, pageable), ReconResultResponse::from);
    }

    @PutMapping("/results/{id}/resolve")
    @Operation(summary = "Mark a recon break as RESOLVED with a note")
    @Transactional
    public ResponseEntity<ReconResultResponse> resolve(
            @PathVariable Long id,
            @Valid @RequestBody ResolutionRequest request) {

        ReconBreak reconBreak = breaks.findById(id)
                .orElseThrow(() ->
                        new TradeNotFoundException("recon_break " + id)
                );

        reconBreak.resolve(request.note());

        ReconBreak savedBreak = breaks.save(reconBreak);

        return ResponseEntity.ok(
                ReconResultResponse.from(savedBreak)
        );
    }
}
