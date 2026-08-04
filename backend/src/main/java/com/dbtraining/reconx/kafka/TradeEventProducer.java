package com.dbtraining.reconx.kafka;

import com.dbtraining.reconx.dto.TradeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.context.ApplicationEventPublisher;

/**
 * ============================================================================
 * TICKET-ADV129 — TradeEventProducer
 *
 * WHAT:    Publishes TradeEvent messages to the `trade-events` Kafka topic.
 * HOW:     KafkaTemplate<String, TradeEvent>. Key = tradeRef so that all
 *          events for the same trade hash to the same partition and
 *          preserve ordering.
 * WHY:     Out-of-order events for the same trade would make event sourcing
 *          impossible (you'd "apply" CREATE after UPDATE).
 * OBSERVE: Kafdrop -> `trade-events` shows one message per published event,
 *          partitioned by tradeRef.
 * ============================================================================
 *
 *  TODO(TICKET-ADV129):
 *    public void publish(TradeEvent event) {
 *        log.debug("Publishing TradeEvent eventId={} ref={} type={}",
 *                  event.eventId(), event.tradeRef(), event.eventType());
 *        template.send(TOPIC, event.tradeRef(), event);
 *    }
 *
 *  GOTCHA: NEVER let a Kafka publish failure roll back the DB transaction.
 *          Publish AFTER commit (use TransactionSynchronizationManager or
 *          @TransactionalEventListener), or accept eventual consistency.
 * ============================================================================
 */
@Component
public class TradeEventProducer {

    private static final Logger log = LoggerFactory.getLogger(TradeEventProducer.class);
    private static final String TOPIC = "trade-events";

    private final KafkaTemplate<String, TradeEvent> template;
    private final ApplicationEventPublisher publisher;

    public TradeEventProducer(KafkaTemplate<String, TradeEvent> template, ApplicationEventPublisher publisher) {
        this.template = template;
        this.publisher = publisher;
    }

    /**
     * Fire-and-forget: a broker outage must never fail the HTTP request or
     * roll back the DB transaction that already committed the trade change,
     * so both the synchronous failure (e.g. TimeoutException from
     * max.block.ms while send() waits on cluster metadata) and the async
     * failure on the returned future are caught and logged, never rethrown.
     */
    public void publish(TradeEvent event) {
        log.debug("Publishing TradeEvent eventId={} ref={} type={}",
                  event.eventId(), event.tradeRef(), event.eventType());
        try {
            template.send(TOPIC, event.tradeRef(), event)
                    .exceptionally(ex -> {
                        log.warn("Failed to publish TradeEvent eventId={} ref={} type={}",
                                 event.eventId(), event.tradeRef(), event.eventType(), ex);
                        return null;
                    });
        } catch (Exception ex) {
            log.warn("Failed to publish TradeEvent eventId={} ref={} type={}",
                      event.eventId(), event.tradeRef(), event.eventType(), ex);
        }
        
        // Also broadcast the event locally for SSE
        publisher.publishEvent(event);
    }
}
