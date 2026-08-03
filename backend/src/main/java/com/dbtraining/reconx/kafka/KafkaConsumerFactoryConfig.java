package com.dbtraining.reconx.kafka;

import com.dbtraining.reconx.dto.SystemAlert;
import com.dbtraining.reconx.dto.TradeEvent;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.MicrometerConsumerListener;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.Map;

/**
 * ============================================================================
 * TICKET-ADV131 / TICKET-ADV133 — named container factories
 * TICKET-ADV139 — Kafka consumer metrics via Micrometer
 *
 * WHAT:    A dedicated ConcurrentKafkaListenerContainerFactory per payload
 *          type, referenced by name from @KafkaListener(containerFactory=...).
 * HOW:     application.yml's spring.kafka.consumer.properties hard-codes
 *          spring.json.value.default.type = TradeEvent, which is only
 *          correct for the trade-events consumers. Each factory here builds
 *          its own ConsumerFactory with the right JsonDeserializer target
 *          type instead of relying on that shared default, and wires in the
 *          retry/DLQ errorHandler bean from KafkaErrorHandlerConfig.
 *          Every ConsumerFactory built here also gets a
 *          MicrometerConsumerListener registered against it (ADV139) — that
 *          listener hooks the factory's consumer-added/removed lifecycle and
 *          binds each underlying KafkaConsumer's client metrics (records
 *          consumed, fetch latency, consumer lag per partition, ...) to the
 *          same MeterRegistry the rest of the app publishes to, tagged with
 *          spring.id=<factory bean name>. Manually-built factories like
 *          these don't get that wiring for free the way Spring Boot's own
 *          autoconfigured ProducerFactory/ConsumerFactory beans do.
 * WHY:     Without a named factory, @KafkaListener(containerFactory = "...")
 *          references a bean that doesn't exist and the application fails
 *          at boot. system-alerts in particular cannot share the
 *          TradeEvent-typed default factory - it needs its own
 *          deserialization target. Without the Micrometer listener, no
 *          Kafka consumer metric ever reaches /actuator/prometheus — the
 *          KafkaDlqGrowing alert in monitoring/prometheus/alerts.yml
 *          references kafka_consumer_records_consumed_total, which only
 *          exists once this listener is attached.
 * OBSERVE: After boot, /actuator/prometheus exposes kafka_consumer_*
 *          series (fetch-latency-avg, records-consumed-total, records-lag,
 *          ...) tagged spring.id=systemAlertListenerContainerFactory-0 /
 *          tradeEventListenerContainerFactory-0.
 *
 * NOTE: this file is expected to collide trivially with the
 * tradeEventListenerContainerFactory added in TICKET-ADV131's PR (#40) -
 * both add one @Bean method to the same class. Whichever PR merges second
 * just re-adds its bean method alongside the other's.
 * ============================================================================
 */
@Configuration
public class KafkaConsumerFactoryConfig {

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, SystemAlert> systemAlertListenerContainerFactory(
            KafkaProperties kafkaProperties,
            DefaultErrorHandler errorHandler,
            MeterRegistry meterRegistry) {
        ConsumerFactory<String, SystemAlert> consumerFactory =
                jsonConsumerFactory(kafkaProperties, SystemAlert.class, meterRegistry);

        ConcurrentKafkaListenerContainerFactory<String, SystemAlert> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, TradeEvent> tradeEventListenerContainerFactory(
            KafkaProperties kafkaProperties,
            DefaultErrorHandler errorHandler,
            MeterRegistry meterRegistry) {
        ConsumerFactory<String, TradeEvent> consumerFactory =
                jsonConsumerFactory(kafkaProperties, TradeEvent.class, meterRegistry);

        ConcurrentKafkaListenerContainerFactory<String, TradeEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }

    private <T> ConsumerFactory<String, T> jsonConsumerFactory(
            KafkaProperties kafkaProperties, Class<T> targetType, MeterRegistry meterRegistry) {
        Map<String, Object> props = kafkaProperties.buildConsumerProperties(null);
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, targetType.getName());
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.dbtraining.reconx.dto");
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);

        DefaultKafkaConsumerFactory<String, T> consumerFactory = new DefaultKafkaConsumerFactory<>(
                props,
                new ErrorHandlingDeserializer<>(new StringDeserializer()),
                new ErrorHandlingDeserializer<>(new JsonDeserializer<>(targetType)));
        consumerFactory.addListener(new MicrometerConsumerListener<>(meterRegistry));
        return consumerFactory;
    }
}
