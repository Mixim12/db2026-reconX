package com.dbtraining.reconx.kafka;

import com.dbtraining.reconx.dto.TradeEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.support.serializer.DeserializationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * TICKET-ADV134 — failed records are retried, then published to {topic}-dlq
 * on the same partition number they arrived on.
 */
class KafkaErrorHandlerConfigTest {

    private final KafkaErrorHandlerConfig config = new KafkaErrorHandlerConfig();

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<Object, Object> template = mock(KafkaTemplate.class);

    @Test
    void errorHandlerIsBackedByADeadLetterPublishingRecoverer() {
        // errorHandler(...) only accepts a DeadLetterPublishingRecoverer, so the
        // "built from a recoverer" criterion is enforced by the signature itself.
        DeadLetterPublishingRecoverer recoverer = config.deadLetterRecoverer(template);

        assertThat(recoverer).isNotNull();
        assertThat(config.errorHandler(recoverer)).isNotNull();
    }

    @Test
    void failedRecordsAreRoutedToTheDlqTopicOnTheSamePartition() {
        TopicPartition destination = resolve(record("trade-events", 2));

        assertThat(destination.topic()).isEqualTo("trade-events-dlq");
        assertThat(destination.partition()).isEqualTo(2);
    }

    @Test
    void dlqNameIsDerivedFromWhicheverTopicFailed() {
        TopicPartition destination = resolve(record("recon-results", 0));

        assertThat(destination.topic()).isEqualTo("recon-results-dlq");
        assertThat(destination.partition()).isZero();
    }

    @Test
    void poisonPillsAreNotRetried() {
        DefaultErrorHandler handler = config.errorHandler(config.deadLetterRecoverer(template));

        assertThat(handler.removeClassification(DeserializationException.class)).isFalse();
        assertThat(handler.removeClassification(IllegalArgumentException.class)).isFalse();
    }

    @Test
    void transientListenerFailuresStillGetTheirRetries() {
        DefaultErrorHandler handler = config.errorHandler(config.deadLetterRecoverer(template));

        // Not on the not-retryable list: a DB blip must burn the retry budget before DLQ.
        assertThat(handler.removeClassification(IllegalStateException.class)).isNull();
    }

    @Test
    void tradeEventContainerFactoryIsWiredToTheErrorHandler() {
        DefaultErrorHandler handler = config.errorHandler(config.deadLetterRecoverer(template));
        ConcurrentKafkaListenerContainerFactory<String, TradeEvent> factory =
                new KafkaConsumerFactoryConfig()
                        .tradeEventListenerContainerFactory(new KafkaProperties(), handler, mock(io.micrometer.core.instrument.MeterRegistry.class));

        ConcurrentMessageListenerContainer<String, TradeEvent> container =
                factory.createContainer("trade-events");

        assertThat(container.getCommonErrorHandler()).isSameAs(handler);
    }

    private static TopicPartition resolve(ConsumerRecord<String, String> failed) {
        return KafkaErrorHandlerConfig.DLQ_DESTINATION
                .apply(failed, new IllegalStateException("listener blew up"));
    }

    private static ConsumerRecord<String, String> record(String topic, int partition) {
        return new ConsumerRecord<>(topic, partition, 17L, "EQU-20260603-0001", "{}");
    }
}
