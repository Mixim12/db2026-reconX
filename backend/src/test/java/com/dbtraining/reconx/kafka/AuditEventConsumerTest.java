package com.dbtraining.reconx.kafka;

import com.dbtraining.reconx.dto.TradeEvent;
import com.dbtraining.reconx.repository.AuditLogRepository;
import com.dbtraining.reconx.repository.entity.AuditLogEntry;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.transaction.annotation.Transactional;


import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * TICKET-ADV132 — AuditEventConsumer persists every TradeEvent to audit_log
 * under its own consumer group so recon and audit both see every event.
 */
class AuditEventConsumerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AuditLogRepository repository = mock(AuditLogRepository.class);
    private final AuditEventConsumer consumer = new AuditEventConsumer(repository);

    @Test
    void listenerConsumesTradeEventsUnderItsOwnConsumerGroup() {
        KafkaListener listener = listenerMethod().getAnnotation(KafkaListener.class);

        assertThat(listener).isNotNull();
        assertThat(listener.topics()).containsExactly("trade-events");
        assertThat(listener.groupId()).isEqualTo("audit-service");
    }

    @Test
    void listenerUsesTheTradeEventContainerFactory() {
        KafkaListener listener = listenerMethod().getAnnotation(KafkaListener.class);

        assertThat(listener.containerFactory()).isEqualTo("tradeEventListenerContainerFactory");
    }

    @Test
    void listenerIsTransactionalSoTheWriteIsBoundToTheInvocation() {
        assertThat(listenerMethod().isAnnotationPresent(Transactional.class)).isTrue();
    }

    @Test
    void everyEventFieldIsMappedOntoTheAuditRow() {
        UUID eventId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-06-03T10:15:30Z");
        TradeEvent event = new TradeEvent(eventId, "EQU-20260603-0001",
                TradeEvent.EventType.TRADE_UPDATED, occurredAt, "trader-a",
                json("{\"notional\":1000}"), json("{\"notional\":2000}"));

        consumer.onTradeEvent(event);

        AuditLogEntry saved = captureSaved().getFirst();
        assertThat(saved.getEventId()).isEqualTo(eventId.toString());
        assertThat(saved.getTradeRef()).isEqualTo("EQU-20260603-0001");
        assertThat(saved.getEventType()).isEqualTo("TRADE_UPDATED");
        assertThat(saved.getEventTimestamp()).isEqualTo(occurredAt);
        assertThat(saved.getActor()).isEqualTo("trader-a");
        assertThat(saved.getBeforeState()).isEqualTo("{\"notional\":1000}");
        assertThat(saved.getAfterState()).isEqualTo("{\"notional\":2000}");
    }

    @Test
    void creationEventsPersistWithoutABeforeSnapshot() {
        consumer.onTradeEvent(event("EQU-20260603-0002", 0,
                TradeEvent.EventType.TRADE_CREATED, null, "{\"notional\":500}"));

        AuditLogEntry saved = captureSaved().getFirst();
        assertThat(saved.getEventType()).isEqualTo("TRADE_CREATED");
        assertThat(saved.getBeforeState()).isNull();
        assertThat(saved.getAfterState()).isEqualTo("{\"notional\":500}");
    }

    @Test
    void tenEventsForOneTradeProduceTenRowsCarryingTheirOwnEventTime() {
        List<TradeEvent> published = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            published.add(event("EQU-20260603-0003", i,
                    TradeEvent.EventType.TRADE_UPDATED, "{\"v\":" + i + "}", "{\"v\":" + (i + 1) + "}"));
        }
        published.forEach(consumer::onTradeEvent);

        List<AuditLogEntry> saved = captureSaved();
        assertThat(saved).hasSize(10);
        assertThat(saved).allMatch(entry -> entry.getTradeRef().equals("EQU-20260603-0003"));
        // Each row must carry its own event's timestamp — not Instant.now(), and not
        // the timestamp of some other event in the batch.
        assertThat(saved).extracting(AuditLogEntry::getEventTimestamp)
                .containsExactlyElementsOf(published.stream().map(TradeEvent::timestamp).toList());
        assertThat(saved).extracting(AuditLogEntry::getEventId)
                .containsExactlyElementsOf(published.stream().map(e -> e.eventId().toString()).toList());
    }

    private List<AuditLogEntry> captureSaved() {
        ArgumentCaptor<AuditLogEntry> captor = ArgumentCaptor.forClass(AuditLogEntry.class);
        verify(repository, atLeastOnce()).save(captor.capture());
        return captor.getAllValues();
    }

    private static TradeEvent event(String tradeRef, int secondsOffset,
                                    TradeEvent.EventType type, String before, String after) {
        return new TradeEvent(UUID.randomUUID(), tradeRef, type,
                Instant.parse("2026-06-03T10:15:30Z").plusSeconds(secondsOffset),
                "trader-a", json(before), json(after));
    }

    /** TradeEvent carries JsonNode snapshots since TICKET-ADV130. */
    private static JsonNode json(String raw) {
        if (raw == null) return null;
        try {
            return MAPPER.readTree(raw);
        } catch (JsonProcessingException e) {
            throw new AssertionError("test fixture is not valid JSON: " + raw, e);
        }
    }

    private static Method listenerMethod() {
        try {
            return AuditEventConsumer.class.getDeclaredMethod("onTradeEvent", TradeEvent.class);
        } catch (NoSuchMethodException e) {
            throw new AssertionError("AuditEventConsumer.onTradeEvent(TradeEvent) is missing", e);
        }
    }
}
