package com.dbtraining.reconx.controller;

import com.dbtraining.reconx.dto.TradeEvent;
import com.dbtraining.reconx.kafka.TradeEventProducer;
import com.dbtraining.reconx.repository.DlqMessageRepository;
import com.dbtraining.reconx.repository.entity.DlqMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * ============================================================================
 * TICKET-ADV136 — DLQ admin replay endpoint
 *
 * WHAT:    POST /api/v1/admin/dlq/replay — re-publishes a single DLQ message
 *          back to the main topic, or previews what would be replayed (dryRun).
 * HOW:     Looks up the DlqMessage row by eventId, deserializes the stored
 *          JSON payload back to a TradeEvent, and publishes via
 *          TradeEventProducer. Deletes the DLQ row on success.
 * WHY:     Operators need a controlled escape hatch to replay individual
 *          messages after a bug fix — bulk replay risks re-DLQ-ing the
 *          same failures.
 * ============================================================================
 */
@RestController
@RequestMapping("/v1/admin/dlq")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "admin-dlq")
@SecurityRequirement(name = "bearerAuth")
public class DlqAdminController {

    private final DlqMessageRepository repo;
    private final TradeEventProducer producer;
    private final ObjectMapper objectMapper;

    public DlqAdminController(DlqMessageRepository repo,
                              TradeEventProducer producer,
                              ObjectMapper objectMapper) {
        this.repo = repo;
        this.producer = producer;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/replay")
    @Operation(summary = "Replay a single DLQ message back to trade-events")
    public ResponseEntity<Map<String, Object>> replay(
            @RequestParam String eventId,
            @RequestParam(defaultValue = "false") boolean dryRun) {

        DlqMessage msg = repo.findByEventId(eventId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND,
                        "No DLQ message found for eventId: " + eventId));

        if (dryRun) {
            return ResponseEntity.ok(Map.of(
                    "dryRun", true,
                    "wouldReplayTo", msg.getOriginalTopic(),
                    "tradeRef", msg.getTradeRef()
            ));
        }

        TradeEvent event;
        try {
            event = objectMapper.readValue(msg.getPayload(), TradeEvent.class);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(NOT_FOUND,
                    "Failed to deserialize stored DLQ payload: " + e.getMessage());
        }

        producer.publish(event);
        repo.delete(msg);

        return ResponseEntity.ok(Map.of(
                "replayed", true,
                "eventId", eventId,
                "topic", msg.getOriginalTopic()
        ));
    }
}
