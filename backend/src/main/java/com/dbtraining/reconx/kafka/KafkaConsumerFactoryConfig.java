package com.dbtraining.reconx.kafka;

import com.dbtraining.reconx.dto.TradeEvent;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.Map;

/**
 * ============================================================================
 * TICKET-ADV131 / TICKET-ADV133 — named container factories
 *
 * WHAT:    A dedicated ConcurrentKafkaListenerContainerFactory per payload
 *          type, referenced by name from @KafkaListener(containerFactory=...).
 * HOW:     application.yml's spring.kafka.consumer.properties hard-codes
 *          spring.json.value.default.type = TradeEvent, which is only
 *          correct for the trade-events consumers. Each factory here builds
 *          its own ConsumerFactory with the right JsonDeserializer target
 *          type instead of relying on that shared default, and wires in the
 *          retry/DLQ errorHandler bean from KafkaErrorHandlerConfig.
 * WHY:     Without a named factory, @KafkaListener(containerFactory = "...")
 *          references a bean that doesn't exist and the application fails
 *          at boot. system-alerts in particular cannot share the
 *          TradeEvent-typed default factory - it needs its own
 *          deserialization target.
 * ============================================================================
 */
@Configuration
public class KafkaConsumerFactoryConfig {

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, TradeEvent> tradeEventListenerContainerFactory(
            KafkaProperties kafkaProperties,
            DefaultErrorHandler errorHandler) {
        ConsumerFactory<String, TradeEvent> consumerFactory =
                jsonConsumerFactory(kafkaProperties, TradeEvent.class);

        ConcurrentKafkaListenerContainerFactory<String, TradeEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }

    private <T> ConsumerFactory<String, T> jsonConsumerFactory(KafkaProperties kafkaProperties, Class<T> targetType) {
        Map<String, Object> props = kafkaProperties.buildConsumerProperties(null);
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, targetType.getName());
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.dbtraining.reconx.dto");
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);

        return new DefaultKafkaConsumerFactory<>(
                props,
                new ErrorHandlingDeserializer<>(new StringDeserializer()),
                new ErrorHandlingDeserializer<>(new JsonDeserializer<>(targetType)));
    }
}
