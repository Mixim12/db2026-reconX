package com.dbtraining.reconx.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

/**
 * ============================================================================
 * TICKET-ADV130 — TradeEvent payload (Kafka envelope)
 *
 * WHAT:    Wire format for trade-events Kafka topic. eventId is the
 *          idempotency key; consumers deduplicate by it.
 * HOW:     Record — Jackson serialises automatically (component model
 *          = default). before/after are JsonNode (structured JSON) so
 *          downstream consumers work with them as objects, not raw strings.
 * WHY:     Including before+after on every event makes downstream consumers
 *          (audit, recon) self-contained — they don't have to fetch the
 *          current state from the DB.
 * ============================================================================
 */
public record TradeEvent(
        UUID eventId,
        String tradeRef,
        EventType eventType,
        Instant timestamp,
        String actor,
        JsonNode before,
        JsonNode after
) {
    public enum EventType {
        TRADE_CREATED, TRADE_UPDATED, TRADE_CANCELLED
    }

    public static TradeEvent created(String tradeRef, JsonNode after) {
        return new TradeEvent(UUID.randomUUID(), tradeRef, EventType.TRADE_CREATED,
                Instant.now(), null, null, after);
    }

    public static TradeEvent updated(String tradeRef, JsonNode before, JsonNode after) {
        return new TradeEvent(UUID.randomUUID(), tradeRef, EventType.TRADE_UPDATED,
                Instant.now(), null, before, after);
    }

    public static TradeEvent cancelled(String tradeRef, JsonNode before) {
        return new TradeEvent(UUID.randomUUID(), tradeRef, EventType.TRADE_CANCELLED,
                Instant.now(), null, before, null);
    }
}
