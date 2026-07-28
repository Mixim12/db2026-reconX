package com.dbtraining.reconx.service;

import com.dbtraining.reconx.dto.TradeEvent;
import com.dbtraining.reconx.repository.AuditLogRepository;
import com.dbtraining.reconx.repository.entity.AuditLogEntry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * TICKET-ADV137 — TradeAggregator.rebuild() fold logic, AuditLogRepository mocked.
 */
class TradeAggregatorTest {

    private final AuditLogRepository auditRepo = mock(AuditLogRepository.class);
    private final TradeAggregator aggregator = new TradeAggregator(auditRepo);

    @Test
    void rebuild_createdThenUpdatedThenCancelled_returnsEmpty() {
        // given
        String ref = "EQU-20260603-0030";
        List<AuditLogEntry> events = List.of(
                entry(ref, TradeEvent.EventType.TRADE_CREATED, 1, "{\"status\":\"PENDING\"}"),
                entry(ref, TradeEvent.EventType.TRADE_UPDATED, 2, "{\"status\":\"CONFIRMED\"}"),
                entry(ref, TradeEvent.EventType.TRADE_CANCELLED, 3, null)
        );
        when(auditRepo.findByTradeRefOrderByEventTimestampAsc(ref)).thenReturn(events);

        // when
        Optional<String> state = aggregator.rebuild(ref);

        // then
        assertThat(state).isEmpty();
    }

    @Test
    void rebuild_createdThenUpdated_withoutCancellation_returnsLastUpdatedSnapshot() {
        // given
        String ref = "EQU-20260603-0031";
        List<AuditLogEntry> events = List.of(
                entry(ref, TradeEvent.EventType.TRADE_CREATED, 1, "{\"status\":\"PENDING\"}"),
                entry(ref, TradeEvent.EventType.TRADE_UPDATED, 2, "{\"status\":\"CONFIRMED\"}")
        );
        when(auditRepo.findByTradeRefOrderByEventTimestampAsc(ref)).thenReturn(events);

        // when
        Optional<String> state = aggregator.rebuild(ref);

        // then
        assertThat(state).contains("{\"status\":\"CONFIRMED\"}");
    }

    @Test
    void rebuild_noEvents_returnsEmpty() {
        // given
        String ref = "EQU-20260603-0032";
        when(auditRepo.findByTradeRefOrderByEventTimestampAsc(ref)).thenReturn(List.of());

        // when
        Optional<String> state = aggregator.rebuild(ref);

        // then
        assertThat(state).isEmpty();
    }

    private AuditLogEntry entry(String ref, TradeEvent.EventType type, long secondsOffset, String afterState) {
        return new AuditLogEntry(
                java.util.UUID.randomUUID().toString(), ref, type.name(),
                Instant.EPOCH.plusSeconds(secondsOffset), "system", null, afterState);
    }
}
