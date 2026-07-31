package com.dbtraining.reconx.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TICKET-ADV130 — TradeEvent is the Kafka wire payload. before/after snapshots
 * are JsonNode (structured JSON), not raw Strings, so downstream consumers can
 * work with them as objects without a secondary parse step.
 */
class TradeEventTest {

    private static final ObjectMapper MAPPER = buildMapper();

    private static ObjectMapper buildMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    @Test
    @DisplayName("is an immutable record")
    void isRecord() {
        assertThat(TradeEvent.class.isRecord()).isTrue();
    }

    @Test
    @DisplayName("exposes no public setters")
    void noSetters() {
        boolean hasSetter = Arrays.stream(TradeEvent.class.getMethods())
                .map(Method::getName)
                .anyMatch(name -> name.startsWith("set"));
        assertThat(hasSetter).isFalse();
    }

    @Test
    @DisplayName("EventType has exactly the three expected constants")
    void eventTypeHasExpectedConstants() {
        assertThat(TradeEvent.EventType.values())
                .containsExactly(
                        TradeEvent.EventType.TRADE_CREATED,
                        TradeEvent.EventType.TRADE_UPDATED,
                        TradeEvent.EventType.TRADE_CANCELLED);
    }

    @Test
    @DisplayName("created() sets TRADE_CREATED, null before, preserved after")
    void createdFactory() {
        JsonNode after = MAPPER.createObjectNode().put("status", "NEW");

        TradeEvent event = TradeEvent.created("TR-1", after);

        assertThat(event.eventId()).isNotNull();
        assertThat(event.timestamp()).isNotNull();
        assertThat(event.tradeRef()).isEqualTo("TR-1");
        assertThat(event.eventType()).isEqualTo(TradeEvent.EventType.TRADE_CREATED);
        assertThat(event.actor()).isNull();
        assertThat(event.before()).isNull();
        assertThat(event.after()).isEqualTo(after);
    }

    @Test
    @DisplayName("updated() sets TRADE_UPDATED and preserves both snapshots")
    void updatedFactory() {
        JsonNode before = MAPPER.createObjectNode().put("status", "NEW");
        JsonNode after = MAPPER.createObjectNode().put("status", "CONFIRMED");

        TradeEvent event = TradeEvent.updated("TR-2", before, after);

        assertThat(event.eventId()).isNotNull();
        assertThat(event.timestamp()).isNotNull();
        assertThat(event.tradeRef()).isEqualTo("TR-2");
        assertThat(event.eventType()).isEqualTo(TradeEvent.EventType.TRADE_UPDATED);
        assertThat(event.actor()).isNull();
        assertThat(event.before()).isEqualTo(before);
        assertThat(event.after()).isEqualTo(after);
    }

    @Test
    @DisplayName("cancelled() sets TRADE_CANCELLED, preserved before, null after")
    void cancelledFactory() {
        JsonNode before = MAPPER.createObjectNode().put("status", "CONFIRMED");

        TradeEvent event = TradeEvent.cancelled("TR-3", before);

        assertThat(event.eventId()).isNotNull();
        assertThat(event.timestamp()).isNotNull();
        assertThat(event.tradeRef()).isEqualTo("TR-3");
        assertThat(event.eventType()).isEqualTo(TradeEvent.EventType.TRADE_CANCELLED);
        assertThat(event.actor()).isNull();
        assertThat(event.before()).isEqualTo(before);
        assertThat(event.after()).isNull();
    }

    @Test
    @DisplayName("successive factory calls produce different eventIds")
    void freshEventIdPerEvent() {
        TradeEvent first = TradeEvent.created("TR-4", MAPPER.createObjectNode());
        TradeEvent second = TradeEvent.created("TR-4", MAPPER.createObjectNode());

        assertThat(first.eventId()).isNotEqualTo(second.eventId());
    }

    @Test
    @DisplayName("Jackson round-trip preserves every field including JsonNode snapshots")
    void jacksonRoundTrip() throws Exception {
        ObjectNode before = MAPPER.createObjectNode();
        before.put("status", "NEW");
        before.put("notional", 1_000_000);

        ObjectNode after = MAPPER.createObjectNode();
        after.put("status", "CONFIRMED");
        after.put("notional", 1_000_000);

        TradeEvent original = new TradeEvent(
                UUID.randomUUID(),
                "TR-5",
                TradeEvent.EventType.TRADE_UPDATED,
                Instant.now().truncatedTo(ChronoUnit.MILLIS),
                "actor-1",
                before,
                after
        );

        String json = MAPPER.writeValueAsString(original);
        TradeEvent roundTripped = MAPPER.readValue(json, TradeEvent.class);

        assertThat(roundTripped.eventId()).isEqualTo(original.eventId());
        assertThat(roundTripped.tradeRef()).isEqualTo(original.tradeRef());
        assertThat(roundTripped.eventType()).isEqualTo(original.eventType());
        assertThat(roundTripped.timestamp()).isEqualTo(original.timestamp());
        assertThat(roundTripped.actor()).isEqualTo(original.actor());
        assertThat(roundTripped.before()).isEqualTo(original.before());
        assertThat(roundTripped.after()).isEqualTo(original.after());
        assertThat(roundTripped).isEqualTo(original);
    }
}
