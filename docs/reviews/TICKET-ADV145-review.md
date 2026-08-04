# TICKET-ADV145 — AI Kafka Consumer Configuration Review

## Review Decision Matrix

| # | Area | Finding | Recommendation | Decision | Rationale |
|---|---|---|---|---|---|
| 1 | Backpressure & Poll Tuning | `max.poll.records` default of 500 risks exceeding `max.poll.interval.ms` on slow processing | `max.poll.records: 100` | **Accept** | Slow downstream processing — keeps poll loop responsive and prevents consumer rebalance. |
| 2 | Error Handling, Retry & DLQ | `ExponentialBackOff` has no random jitter during retries | Add jitter via custom `BackOff` implementation | **Defer** | Prevents thundering herd on transient DB outages; logged as a backlog item for next sprint. |
| 3 | Idempotence & EOS | Producer `enable.idempotence` is not explicitly declared | `enable.idempotence: true` | **Accept** | Cheap insurance against duplicate record publishing during network retries. |
| 4 | Observability | Kafka metrics lack an explicit application tag | `management.metrics.tags.application: reconx` | **Accept** | Prevents metric collision across microservices in shared Prometheus instances. |
| 5 | Security | Consumer `bootstrap-servers` connects over `PLAINTEXT` | Use `SASL_SSL` | **Reject** | Known dev/test environment trade-off; TLS/SASL infrastructure setup is deferred to Day 10 prod deployment. |

## Applied Configuration Updates

### `application.yml`
- Added `spring.kafka.consumer.properties.max.poll.records: 100`
- Added `spring.kafka.producer.properties.enable.idempotence: true`
- Added `management.metrics.tags.application: reconx`
