package com.dbtraining.reconx.kafka;

import com.dbtraining.reconx.dto.TradeEvent;
import com.dbtraining.reconx.repository.DlqMessageRepository;
import com.dbtraining.reconx.repository.entity.DlqMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * ============================================================================
 * TICKET-ADV136 — DLQ consumer
 *
 * WHAT:    Listens on trade-events-dlq, persists each failed message as a
 *          DlqMessage row so admins can inspect and replay via the admin
 *          endpoint.
 * HOW:     @KafkaListener with groupId dlq-monitor, using the same
 *          tradeEventListenerContainerFactory as the main consumers.
 * WHY:     Without persistence, dead-lettered messages are invisible —
 *          ops cannot see what failed or replay after a fix.
 * OBSERVE: Force a deserialization error on trade-events; the message
 *          lands here and a row appears in dlq_messages.
 * ============================================================================
 */
@Component
public class DlqConsumer {

    private static final Logger log = LoggerFactory.getLogger(DlqConsumer.class);

    private final DlqMessageRepository repo;
    private final ObjectMapper objectMapper;

    public DlqConsumer(DlqMessageRepository repo, ObjectMapper objectMapper) {
        this.repo = repo;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "trade-events-dlq",
            groupId = "dlq-monitor",
            containerFactory = "tradeEventListenerContainerFactory"
    )
    public void onDlqMessage(ConsumerRecord<String, TradeEvent> record,
                             @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exMsg) {
        TradeEvent event = record.value();
        log.error("DLQ: trade={} eventId={} topic={} partition={} offset={} reason={}",
                event.tradeRef(), event.eventId(),
                record.topic(), record.partition(), record.offset(), exMsg);

        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            payloadJson = event.toString();
        }

        repo.save(DlqMessage.builder()
                .eventId(event.eventId().toString())
                .tradeRef(event.tradeRef())
                .originalTopic(record.topic().replace("-dlq", ""))
                .partition(record.partition())
                .offset(record.offset())
                .payload(payloadJson)
                .reason(exMsg)
                .firstSeen(Instant.now())
                .build());
    }
}
