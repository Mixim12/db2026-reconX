package com.dbtraining.reconx.dto;

/**
 * TICKET-ADV133 — wire format for the `system-alerts` Kafka topic.
 * severity/code/message are distinct fields (not one flattened string) so
 * consumers can filter/route without parsing.
 */
public record SystemAlert(String severity, String code, String message) {}
