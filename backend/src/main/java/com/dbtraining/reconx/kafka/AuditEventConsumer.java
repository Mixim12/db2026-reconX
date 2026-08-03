package com.dbtraining.reconx.kafka;

import com.dbtraining.reconx.dto.TradeEvent;
import com.dbtraining.reconx.repository.AuditLogRepository;
import com.dbtraining.reconx.repository.entity.AuditLogEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * ============================================================================
 * TICKET-ADV132 — AuditEventConsumer
 *
 * WHAT:    Consumer on trade-events (groupId audit-service) that writes every
 *          received TradeEvent into the audit_log table.
 * HOW:     Maps TradeEvent fields -> AuditLogEntry JPA entity and calls
 *          auditRepo.save().
 * WHY:     Decouples audit logging from the HTTP request thread and from the
 *          trade creation/update transaction. Every state change is recorded
 *          for compliance and event-sourcing rebuild (TICKET-ADV137).
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
                e.before() != null ? e.before().toString() : null,
                e.after() != null ? e.after().toString() : null));
        log.debug("Audit row persisted for eventId={} ref={}", e.eventId(), e.tradeRef());
    }
}
