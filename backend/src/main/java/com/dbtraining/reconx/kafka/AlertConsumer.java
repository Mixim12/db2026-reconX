package com.dbtraining.reconx.kafka;

import com.dbtraining.reconx.dto.SystemAlert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * ============================================================================
 * TICKET-ADV133 — AlertConsumer
 *
 * WHAT:    Subscribes to `system-alerts` and (for the training project) logs
 *          the payload. In a real environment this is where Slack / PagerDuty
 *          / e-mail fan-out would happen.
 * HOW:     @KafkaListener on the `system-alerts` topic, groupId
 *          `alert-service`.
 * WHY:     Decouples alert producers (any service) from alert sinks
 *          (notification channels).
 * OBSERVE: Publish a string to `system-alerts` via Kafdrop -> a WARN line
 *          appears in the app log.
 * ============================================================================
 *
 *  TODO(TICKET-ADV133):
 *    @KafkaListener(topics = "system-alerts", groupId = "alert-service")
 *    public void onAlert(String payload) {
 *        log.warn("ALERT: {}", payload);
 *    }
 * ============================================================================
 */
@Component
public class AlertConsumer {

    private static final Logger log = LoggerFactory.getLogger(AlertConsumer.class);

    private final AlertSink alertSink;

    public AlertConsumer(AlertSink alertSink) {
        this.alertSink = alertSink;
    }

    // NOTE (TICKET-ADV133 blocker): containerFactory references a bean named
    // "systemAlertListenerContainerFactory" that does not exist anywhere in
    // the repo yet - it matters here specifically because SystemAlert needs
    // different deserialization config than the TradeEvent consumers. This
    // listener will fail to start until that bean lands - flagged here
    // rather than silently assumed working.
    @KafkaListener(topics = "system-alerts", groupId = "alert-service",
                   containerFactory = "systemAlertListenerContainerFactory")
    public void onAlert(SystemAlert alert) {
        log.warn("ALERT severity={} code={} message={}",
                 alert.severity(), alert.code(), alert.message());
        alertSink.notify(alert);
    }
}
