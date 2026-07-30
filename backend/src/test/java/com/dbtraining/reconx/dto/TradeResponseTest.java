package com.dbtraining.reconx.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TICKET-ADV053 — TradeResponse is a flat record; no JPA entity may leak into it.
 */
class TradeResponseTest {

    @Test
    @DisplayName("carries flat counterparty and instrument fields")
    void carriesFlatRelationFields() {
        assertThat(TradeResponse.class.isRecord()).isTrue();

        List<String> names = Arrays.stream(TradeResponse.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();

        assertThat(names).contains(
                "counterpartyId", "counterpartyName", "instrumentId", "instrumentSymbol",
                "id", "tradeRef", "quantity", "price", "tradeDate", "status",
                "createdAt", "modifiedAt");
    }

    @Test
    @DisplayName("status is exposed as a String, never as an enum")
    void statusIsString() {
        assertThat(Arrays.stream(TradeResponse.class.getRecordComponents())
                .filter(c -> c.getName().equals("status"))
                .findFirst()
                .orElseThrow()
                .getType()).isEqualTo(String.class);
    }

    @Test
    @DisplayName("no component type comes from the JPA entity package")
    void noEntityLeaks() {
        assertThat(Arrays.stream(TradeResponse.class.getRecordComponents())
                .map(c -> c.getType().getName())
                .filter(n -> n.startsWith("com.dbtraining.reconx.repository.entity"))
                .toList())
                .isEmpty();
    }
}
