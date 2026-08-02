package com.dbtraining.reconx.kafka;

import com.dbtraining.reconx.dto.TradeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

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

    public TradeEventProducer(KafkaTemplate<String, TradeEvent> template) {
        this.template = template;
    }

    /**
     * Publishing is best-effort by design: the trade is already persisted, and a
     * broker outage must not roll back the caller's transaction (see the GOTCHA
     * above). KafkaTemplate.send throws synchronously when it cannot fetch topic
     * metadata, and reports later failures through the returned future — both
     * paths are logged and swallowed here rather than surfaced as a 500.
     */
    public void publish(TradeEvent event) {
        log.debug("Publishing TradeEvent eventId={} ref={} type={}",
                  event.eventId(), event.tradeRef(), event.eventType());
        try {
            template.send(TOPIC, event.tradeRef(), event)
                    .whenComplete((result, failure) -> {
                        if (failure != null) {
                            log.error("Failed to publish TradeEvent eventId={} ref={} type={}",
                                      event.eventId(), event.tradeRef(), event.eventType(), failure);
                        }
                    });
        } catch (Exception ex) {
            log.error("Failed to publish TradeEvent eventId={} ref={} type={}",
                      event.eventId(), event.tradeRef(), event.eventType(), ex);
        }
    }
}
