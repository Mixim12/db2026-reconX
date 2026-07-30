package com.dbtraining.reconx.kafka;

import com.dbtraining.reconx.dto.SystemAlert;

/**
 * TICKET-ADV133 — fan-out target for system-alerts (Slack/PagerDuty/email in
 * a real environment). Kept as an interface so AlertConsumer doesn't depend
 * on a concrete notification channel.
 */
public interface AlertSink {
    void notify(SystemAlert alert);
}
