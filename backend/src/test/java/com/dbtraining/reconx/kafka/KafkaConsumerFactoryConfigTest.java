package com.dbtraining.reconx.kafka;

import com.dbtraining.reconx.dto.SystemAlert;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TICKET-ADV139 — verifies the MicrometerConsumerListener wired into
 * jsonConsumerFactory actually registers Kafka client metrics once a
 * consumer is created — no live broker required, since building a
 * KafkaConsumer is lazy (network calls only happen on poll()/metadata
 * fetch), which is exactly when MicrometerConsumerListener.consumerAdded
 * fires.
 */
class KafkaConsumerFactoryConfigTest {

    @Test
    void creatingAConsumerRegistersKafkaMetricsOnTheSharedRegistry() {
        KafkaConsumerFactoryConfig config = new KafkaConsumerFactoryConfig();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        KafkaProperties kafkaProperties = new KafkaProperties();
        kafkaProperties.setBootstrapServers(List.of("localhost:9092"));

        ConcurrentKafkaListenerContainerFactory<String, SystemAlert> factory =
                config.systemAlertListenerContainerFactory(
                        kafkaProperties, new DefaultErrorHandler(), registry);

        assertThat(registry.getMeters()).isEmpty();

        Consumer<?, ?> consumer =
                factory.getConsumerFactory().createConsumer("metrics-test-group", "metrics-test-client");
        try {
            assertThat(registry.getMeters())
                    .as("MicrometerConsumerListener should bind kafka.consumer.* metrics once a consumer exists")
                    .isNotEmpty()
                    .anyMatch(meter -> meter.getId().getName().startsWith("kafka.consumer"));
        } finally {
            consumer.close();
        }
    }
}
