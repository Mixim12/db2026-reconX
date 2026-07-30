package com.dbtraining.reconx.observability;

import org.apache.kafka.clients.admin.AdminClientConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaHealthIndicatorTest {

    @Test
    void isConditionalOnKafkaBootstrapServers() {
        ConditionalOnProperty condition = AnnotationUtils.findAnnotation(
                KafkaHealthIndicator.class, ConditionalOnProperty.class);

        assertThat(condition).isNotNull();
        assertThat(condition.name()).containsExactly("spring.kafka.bootstrap-servers");

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(KafkaHealthIndicator.class);
            context.refresh();
            assertThat(context.containsBean("reconxKafka")).isFalse();
        }

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(
                    new org.springframework.core.env.MapPropertySource(
                            "test", Map.of("spring.kafka.bootstrap-servers", "broker:9092")));
            context.register(KafkaHealthIndicator.class);
            context.refresh();
            assertThat(context.containsBean("reconxKafka")).isTrue();
        }
    }

    @Test
    void reportsClusterDetailsAndUsesRequiredTimeouts() throws Exception {
        Map<String, Object> capturedConfig = new java.util.HashMap<>();
        KafkaHealthIndicator indicator = new KafkaHealthIndicator(
                "broker:9092", config -> {
                    capturedConfig.putAll(config);
                    return new KafkaHealthIndicator.ClusterDetails("cluster-1", 2);
                });

        Health health = indicator.health();

        assertThat(health.getStatus().getCode()).isEqualTo("UP");
        assertThat(health.getDetails())
                .containsEntry("clusterId", "cluster-1")
                .containsEntry("nodeCount", 2);
        assertThat(capturedConfig)
                .containsEntry(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, "broker:9092")
                .containsEntry(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 2_000)
                .containsEntry(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 3_000);
    }
}
