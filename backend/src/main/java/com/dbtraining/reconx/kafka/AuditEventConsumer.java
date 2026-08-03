package com.dbtraining.reconx.kafka;

import com.dbtraining.reconx.dto.TradeEvent;
import com.dbtraining.reconx.repository.AuditLogRepository;
import com.dbtraining.reconx.repository.entity.AuditLogEntry;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * ============================================================================
 * TICKET-ADV132 — AuditEventConsumer
 *
 * WHAT:    Persists every TradeEvent flowing through `trade-events` into the
 *          audit_log table.
 * HOW:     @KafkaListener on `trade-events`, groupId `audit-service`. Maps
 *          the TradeEvent DTO -> AuditLogEntry entity -> repo.save(...).
 * WHY:     Together with ADV137 this powers event-sourced replay — every
 *          domain change is captured immutably.
 * OBSERVE: After a POST /api/v1/trades, query audit_log -> one new row with
 *          the same eventId.
 * ============================================================================
 *
 *  HINT: The consumer is on a DIFFERENT groupId from ReconciliationConsumer
 *        so Kafka delivers each message to both groups independently.
 * ============================================================================
 */
@Component
public class AuditEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(AuditEventConsumer.class);
    private final AuditLogRepository repo;

    public AuditEventConsumer(AuditLogRepository repo) { this.repo = repo; }

    @KafkaListener(
            topics = "trade-events",
            groupId = "audit-service",
            containerFactory = "tradeEventListenerContainerFactory")
    @Transactional
    public void onTradeEvent(TradeEvent e) {
        repo.save(new AuditLogEntry(
                e.eventId().toString(),
                e.tradeRef(),
                e.eventType().name(),
                e.timestamp(),
                e.actor(),
                asJsonText(e.before()),
                asJsonText(e.after())));
        log.debug("Audit row persisted for eventId={} ref={}", e.eventId(), e.tradeRef());
    }

    /**
     * TradeEvent carries structured snapshots (JsonNode, TICKET-ADV130) but
     * audit_log.before_state / after_state are TEXT columns that ADV137's
     * replay reads back as raw JSON. Serialise at the persistence boundary so
     * the wire format stays structured and the table stays portable.
     * A null snapshot (creates have no before, cancels have no after) stays null.
     */
    private static String asJsonText(JsonNode snapshot) {
        return snapshot == null || snapshot.isNull() ? null : snapshot.toString();
    }
}
