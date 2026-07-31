package com.dbtraining.reconx.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TICKET-ADV128 — verifies the four Kafka topics (trade-events, trade-events-dlq,
 * recon-results, system-alerts) are declared as NewTopic @Bean methods with the
 * correct partition counts, replication factor, and wire names.
 */
class KafkaTopicsConfigTest {

    private final KafkaTopicsConfig config = new KafkaTopicsConfig();

    @Test
    void testTopicNameConstants_holdExactWireNames() {
        assertThat(KafkaTopicsConfig.TRADE_EVENTS).isEqualTo("trade-events");
        assertThat(KafkaTopicsConfig.TRADE_EVENTS_DLQ).isEqualTo("trade-events-dlq");
        assertThat(KafkaTopicsConfig.RECON_RESULTS).isEqualTo("recon-results");
        assertThat(KafkaTopicsConfig.SYSTEM_ALERTS).isEqualTo("system-alerts");
    }

    @Test
    void testTradeEventsTopic_hasThreePartitionsAndReplicationFactorOne() {
        NewTopic topic = config.tradeEvents();

        assertThat(topic.name()).isEqualTo(KafkaTopicsConfig.TRADE_EVENTS);
        assertThat(topic.numPartitions()).isEqualTo(3);
        assertThat(topic.replicationFactor()).isEqualTo((short) 1);
    }

    @Test
    void testTradeEventsDlqTopic_partitionCountMatchesMainTopic() {
        NewTopic tradeEvents = config.tradeEvents();
        NewTopic dlq = config.tradeEventsDlq();

        assertThat(dlq.name()).isEqualTo(KafkaTopicsConfig.TRADE_EVENTS_DLQ);
        // Real invariant: DLQ partition count MUST equal the main topic's partition
        // count so the DeadLetterPublishingRecoverer preserves partition numbers.
        assertThat(dlq.numPartitions()).isEqualTo(tradeEvents.numPartitions());
        assertThat(dlq.replicationFactor()).isEqualTo((short) 1);
    }

    @Test
    void testReconResultsTopic_hasTwoPartitionsAndReplicationFactorOne() {
        NewTopic topic = config.reconResults();

        assertThat(topic.name()).isEqualTo(KafkaTopicsConfig.RECON_RESULTS);
        assertThat(topic.numPartitions()).isEqualTo(2);
        assertThat(topic.replicationFactor()).isEqualTo((short) 1);
    }

    @Test
    void testSystemAlertsTopic_hasOnePartitionAndReplicationFactorOne() {
        NewTopic topic = config.systemAlerts();

        assertThat(topic.name()).isEqualTo(KafkaTopicsConfig.SYSTEM_ALERTS);
        assertThat(topic.numPartitions()).isEqualTo(1);
        assertThat(topic.replicationFactor()).isEqualTo((short) 1);
    }

    @Test
    void testAllFourTopicMethods_areRegisteredAsSpringBeans() {
        List<String> beanMethodNames = Arrays.stream(KafkaTopicsConfig.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Bean.class))
                .filter(method -> NewTopic.class.isAssignableFrom(method.getReturnType()))
                .map(Method::getName)
                .toList();

        assertThat(beanMethodNames)
                .as("KafkaTopicsConfig must expose exactly 4 @Bean NewTopic methods")
                .hasSize(4);
    }
}
