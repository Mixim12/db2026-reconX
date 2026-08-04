package com.dbtraining.reconx.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.util.backoff.ExponentialBackOff;

import java.util.function.BiFunction;

/**
 * ============================================================================
 * TICKET-ADV134 — DLQ via DeadLetterPublishingRecoverer (failed messages
 *                routed to {topic}-dlq with the same partition number)
 * TICKET-ADV135 — Retry strategy: 3 attempts with exponential backoff
 *                (1s, 2s, 4s) before giving up to DLQ
 *
 * WHAT:    Spring Kafka error handler that retries with backoff and on
 *          final failure publishes the poison record to the corresponding
 *          DLQ topic.
 * HOW:     One @Bean DefaultErrorHandler combining a
 *          DeadLetterPublishingRecoverer + ExponentialBackOff.
 * WHY:     Without this, an exception in a listener kills the consumer
 *          thread and the whole partition stalls. With it, retries happen,
 *          and a final failure is observable (DLQ topic) rather than lost.
 * OBSERVE: Force an exception in a consumer — Kafdrop should show the
 *          record on `trade-events-dlq` with the same partition as the
 *          original.
 * ============================================================================
 *
 *  GOTCHA: trade-events-dlq must already exist (TICKET-ADV128). The
 *          recoverer does NOT auto-create the topic.
 * ============================================================================
 */
@Configuration
public class KafkaErrorHandlerConfig {

    /**
     * Routes a failed record to the DLQ of its own topic, preserving the partition
     * number so per-tradeRef ordering still holds inside the DLQ.
     */
    static final BiFunction<ConsumerRecord<?, ?>, Exception, TopicPartition> DLQ_DESTINATION =
            (rec, ex) -> new TopicPartition(rec.topic() + "-dlq", rec.partition());

    @Bean
    public DeadLetterPublishingRecoverer deadLetterRecoverer(KafkaTemplate<Object, Object> template) {
        return new DeadLetterPublishingRecoverer(template, DLQ_DESTINATION);
    }

    @Bean
    public DefaultErrorHandler errorHandler(DeadLetterPublishingRecoverer recoverer) {
        ExponentialBackOff backoff = new ExponentialBackOff(1000L, 2.0);
        // TICKET-ADV135: the criterion is a total time budget (~8s), not an
        // attempt count. setMaxAttempts(3) compiles fine (it exists on
        // ExponentialBackOff in Spring 6.2) but caps by *count*, not time -
        // CI wouldn't catch the difference either way. 1s+2s+4s = 7s < 8s,
        // so this still yields 3 retries in practice.
        backoff.setMaxElapsedTime(8000L);

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backoff);
        // A payload that cannot be deserialized (or is structurally invalid) will fail
        // identically on every attempt — send it straight to the DLQ instead of burning
        // the 8s retry budget on it.
        handler.addNotRetryableExceptions(DeserializationException.class, IllegalArgumentException.class);
        return handler;
    }
}
