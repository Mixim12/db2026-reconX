package com.dbtraining.reconx.kafka;

import com.dbtraining.reconx.dto.TradeEvent;
import com.dbtraining.reconx.repository.AuditLogRepository;
import com.dbtraining.reconx.repository.entity.AuditLogEntry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;


import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditEventConsumerTest {

    @Mock
    private AuditLogRepository repository;

    private AuditEventConsumer consumer;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        consumer = new AuditEventConsumer(repository);
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
                jsonNode("{\"notional\":1000}"), jsonNode("{\"notional\":2000}"));

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
                "trader-a", jsonNode(before), jsonNode(after));
    }

    private static JsonNode jsonNode(String json) {
        if (json == null) return null;
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException(e);
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
