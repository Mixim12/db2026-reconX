package com.dbtraining.reconx.kafka;

import com.dbtraining.reconx.dto.SystemAlert;
import org.springframework.stereotype.Component;

/**
 * TICKET-ADV133 — default AlertSink for the training project: no real
 * notification channel (Slack/PagerDuty/email) is wired up, so this is a
 * deliberate no-op. AlertConsumer already logs at WARN before calling the
 * sink, so alerts are still observable via the app log.
 */
@Component
public class NoopAlertSink implements AlertSink {
    @Override
    public void notify(SystemAlert alert) {
        // Intentionally empty - see class Javadoc.
    }
}
