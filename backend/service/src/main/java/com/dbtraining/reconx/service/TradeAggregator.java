package com.dbtraining.reconx.service;

import com.dbtraining.reconx.dto.TradeEvent;
import com.dbtraining.reconx.repository.AuditLogRepository;
import com.dbtraining.reconx.repository.entity.AuditLogEntry;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * ============================================================================
 * TICKET-ADV137 — Event sourcing rebuild
 *
 * WHAT:    Reconstructs the current state of a trade purely from its
 *          audit_log event stream.
 * HOW:     Reads every AuditLogEntry for a tradeRef, oldest first, and folds
 *          them into a running "after state" snapshot: CREATED/UPDATED
 *          overwrite it, CANCELLED clears it.
 * WHY:     Proves the audit log (TICKET-ADV132) is a real event source, not
 *          just a log — any trade can be replayed from its events alone.
 * OBSERVE: A CREATED -> UPDATED -> CANCELLED sequence rebuilds to
 *          Optional.empty(); without the CANCELLED it returns the last
 *          UPDATED snapshot.
 * ============================================================================
 */
@Service
public class TradeAggregator {

    private final AuditLogRepository auditRepo;

    public TradeAggregator(AuditLogRepository auditRepo) {
        this.auditRepo = auditRepo;
    }

    /**
     * Replay every event for {@code tradeRef} and fold it into the current state.
     *
     * @param tradeRef the trade reference to rebuild
     * @return the last known "after" snapshot, or {@link Optional#empty()} if
     *         there are no events for this trade, or the trade's last event
     *         was a cancellation
     */
    public Optional<String> rebuild(String tradeRef) {
        List<AuditLogEntry> events = auditRepo.findByTradeRefOrderByEventTimestampAsc(tradeRef);
        if (events.isEmpty()) {
            return Optional.empty();
        }

        String state = null;
        for (AuditLogEntry e : events) {
            switch (TradeEvent.EventType.valueOf(e.getEventType())) {
                case TRADE_CREATED, TRADE_UPDATED -> state = e.getAfterState();
                case TRADE_CANCELLED              -> state = null;
            }
        }
        return Optional.ofNullable(state);
    }
}
