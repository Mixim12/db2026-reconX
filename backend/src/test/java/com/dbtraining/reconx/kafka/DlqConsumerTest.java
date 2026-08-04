package com.dbtraining.reconx.kafka;

import com.dbtraining.reconx.dto.TradeEvent;
import com.dbtraining.reconx.repository.DlqMessageRepository;
import com.dbtraining.reconx.repository.entity.DlqMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * TICKET-ADV136 — DlqConsumer persists failed messages from trade-events-dlq.
 */
@ExtendWith(MockitoExtension.class)
class DlqConsumerTest {

    @Mock
    private DlqMessageRepository repo;

    @Captor
    private ArgumentCaptor<DlqMessage> messageCaptor;

    private DlqConsumer consumer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        objectMapper.findAndRegisterModules();
        consumer = new DlqConsumer(repo, objectMapper);
    }

    @Test
    @DisplayName("Listener persists a DlqMessage with all required fields from ConsumerRecord")
    void listenerPersistsDlqMessageWithAllFields() {
        UUID eventId = UUID.randomUUID();
        TradeEvent event = TradeEvent.created("EQU-20260603-0001", null);
        // Build a TradeEvent with a known eventId via the record constructor
        event = new TradeEvent(eventId, "EQU-20260603-0001",
                TradeEvent.EventType.TRADE_CREATED, Instant.now(), "tester", null, null);

        ConsumerRecord<String, TradeEvent> record =
                new ConsumerRecord<>("trade-events-dlq", 2, 42L, event.tradeRef(), event);

        String exceptionMsg = "Deserialization error: malformed JSON";

        consumer.onDlqMessage(record, exceptionMsg);

        verify(repo).save(messageCaptor.capture());
        DlqMessage saved = messageCaptor.getValue();

        assertThat(saved.getEventId()).isEqualTo(eventId.toString());
        assertThat(saved.getTradeRef()).isEqualTo("EQU-20260603-0001");
        assertThat(saved.getOriginalTopic()).isEqualTo("trade-events");
        assertThat(saved.getPartition()).isEqualTo(2);
        assertThat(saved.getOffset()).isEqualTo(42L);
        assertThat(saved.getReason()).isEqualTo("Deserialization error: malformed JSON");
        assertThat(saved.getFirstSeen()).isNotNull();
        assertThat(saved.getPayload()).isNotBlank();
    }

    @Test
    @DisplayName("Original topic is derived by stripping -dlq suffix")
    void originalTopicStrippedOfDlqSuffix() {
        TradeEvent event = new TradeEvent(UUID.randomUUID(), "FX-001",
                TradeEvent.EventType.TRADE_UPDATED, Instant.now(), null, null, null);
        ConsumerRecord<String, TradeEvent> record =
                new ConsumerRecord<>("trade-events-dlq", 0, 10L, event.tradeRef(), event);

        consumer.onDlqMessage(record, "some error");

        verify(repo).save(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getOriginalTopic()).isEqualTo("trade-events");
    }

    @Test
    @DisplayName("Payload is stored as JSON string representation of the TradeEvent")
    void payloadIsStoredAsJsonString() {
        UUID eventId = UUID.randomUUID();
        TradeEvent event = new TradeEvent(eventId, "BOND-001",
                TradeEvent.EventType.TRADE_CANCELLED, Instant.now(), "admin", null, null);
        ConsumerRecord<String, TradeEvent> record =
                new ConsumerRecord<>("trade-events-dlq", 1, 5L, event.tradeRef(), event);

        consumer.onDlqMessage(record, "processing error");

        verify(repo).save(messageCaptor.capture());
        String payload = messageCaptor.getValue().getPayload();
        assertThat(payload).contains(eventId.toString());
        assertThat(payload).contains("BOND-001");
    }
}
