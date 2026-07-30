package com.dbtraining.reconx.dto;

import com.dbtraining.reconx.repository.entity.Counterparty;
import com.dbtraining.reconx.repository.entity.Instrument;
import com.dbtraining.reconx.repository.entity.Trade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * TICKET-ADV054 — MapStruct TradeMapper.
 *
 * Exercises the generated TradeMapperImpl directly: if MapStruct did not run,
 * this test does not compile — which is exactly the signal we want.
 */
class TradeMapperTest {

    private final TradeMapper mapper = new TradeMapperImpl();

    private static Counterparty counterparty() {
        Counterparty cp = mock(Counterparty.class);
        when(cp.getId()).thenReturn(7L);
        when(cp.getName()).thenReturn("ACME BANK");
        return cp;
    }

    private static Instrument instrument() {
        Instrument inst = mock(Instrument.class);
        when(inst.getId()).thenReturn(3L);
        when(inst.getSymbol()).thenReturn("VOD.L");
        return inst;
    }

    private static Trade trade(String status) {
        Counterparty cp = counterparty();
        Instrument inst = instrument();

        Trade t = new Trade();
        t.setTradeRef("ABC-20260130-0001");
        t.setCounterparty(cp);
        t.setInstrument(inst);
        t.setAssetClass("EQUITY");
        t.setSide("BUY");
        t.setQuantity(new BigDecimal("100.0000"));
        t.setPrice(new BigDecimal("12.3456"));
        t.setTradeDate(LocalDate.of(2026, 1, 30));
        t.setStatus(status);
        return t;
    }

    @Test
    @DisplayName("toResponse flattens counterparty and instrument into scalar fields")
    void toResponseFlattensRelations() {
        TradeResponse response = mapper.toResponse(trade("PENDING"));

        assertThat(response.counterpartyId()).isEqualTo(7L);
        assertThat(response.counterpartyName()).isEqualTo("ACME BANK");
        assertThat(response.instrumentId()).isEqualTo(3L);
        assertThat(response.instrumentSymbol()).isEqualTo("VOD.L");
    }

    @Test
    @DisplayName("toResponse copies the scalar trade fields verbatim")
    void toResponseCopiesScalars() {
        TradeResponse response = mapper.toResponse(trade("MATCHED"));

        assertThat(response.tradeRef()).isEqualTo("ABC-20260130-0001");
        assertThat(response.assetClass()).isEqualTo("EQUITY");
        assertThat(response.side()).isEqualTo("BUY");
        assertThat(response.quantity()).isEqualByComparingTo("100.0000");
        assertThat(response.price()).isEqualByComparingTo("12.3456");
        assertThat(response.tradeDate()).isEqualTo(LocalDate.of(2026, 1, 30));
    }

    @Test
    @DisplayName("status is converted through the @Named helper, null-safely")
    void statusConvertedViaNamedHelper() {
        assertThat(mapper.toResponse(trade("MATCHED")).status()).isEqualTo("MATCHED");
        assertThat(mapper.toResponse(trade(null)).status()).isNull();
        assertThat(TradeMapper.statusToString(null)).isNull();
    }

    @Test
    @DisplayName("toResponse of a null trade is null")
    void toResponseNullSafe() {
        assertThat(mapper.toResponse(null)).isNull();
    }

    @Test
    @DisplayName("toEntity copies wire fields and leaves service-owned fields untouched")
    void toEntityIgnoresServiceOwnedFields() {
        TradeRequest req = new TradeRequest(
                "ABC-20260130-0002", 3L, 7L, "EQUITY", "SELL",
                new BigDecimal("50.0000"), new BigDecimal("9.8765"),
                LocalDate.of(2026, 1, 29));

        Trade entity = mapper.toEntity(req);

        assertThat(entity.getTradeRef()).isEqualTo("ABC-20260130-0002");
        assertThat(entity.getAssetClass()).isEqualTo("EQUITY");
        assertThat(entity.getSide()).isEqualTo("SELL");
        assertThat(entity.getQuantity()).isEqualByComparingTo("50.0000");
        assertThat(entity.getPrice()).isEqualByComparingTo("9.8765");
        assertThat(entity.getTradeDate()).isEqualTo(LocalDate.of(2026, 1, 29));

        assertThat(entity.getId()).isNull();
        assertThat(entity.getCounterparty()).isNull();
        assertThat(entity.getInstrument()).isNull();
        assertThat(entity.getCreatedAt()).isNull();
        assertThat(entity.getModifiedAt()).isNull();
    }

    /**
     * ReportingPolicy.ERROR is a compile-time contract, so assert its effect:
     * every component of TradeResponse must be populated from a fully-populated
     * Trade. A field left unmapped could not survive the build.
     */
    @Test
    @DisplayName("no TradeResponse field is left unmapped")
    void everyResponseFieldIsPopulated() {
        Counterparty cp = counterparty();
        Instrument inst = instrument();

        Trade trade = mock(Trade.class);
        when(trade.getId()).thenReturn(42L);
        when(trade.getTradeRef()).thenReturn("ABC-20260130-0001");
        when(trade.getCounterparty()).thenReturn(cp);
        when(trade.getInstrument()).thenReturn(inst);
        when(trade.getAssetClass()).thenReturn("EQUITY");
        when(trade.getSide()).thenReturn("BUY");
        when(trade.getQuantity()).thenReturn(new BigDecimal("100.0000"));
        when(trade.getPrice()).thenReturn(new BigDecimal("12.3456"));
        when(trade.getTradeDate()).thenReturn(LocalDate.of(2026, 1, 30));
        when(trade.getStatus()).thenReturn("PENDING");
        when(trade.getCreatedAt()).thenReturn(Instant.parse("2026-01-30T09:00:00Z"));
        when(trade.getModifiedAt()).thenReturn(Instant.parse("2026-01-30T10:00:00Z"));

        TradeResponse response = mapper.toResponse(trade);

        List<String> nullComponents = Arrays.stream(TradeResponse.class.getRecordComponents())
                .filter(component -> readComponent(component, response) == null)
                .map(RecordComponent::getName)
                .toList();

        assertThat(nullComponents).isEmpty();
    }

    private static Object readComponent(RecordComponent component, TradeResponse response) {
        try {
            return component.getAccessor().invoke(response);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("cannot read " + component.getName(), e);
        }
    }

    @Test
    @DisplayName("toEntity leaves status at the entity default — the service owns it")
    void toEntityLeavesStatusToTheService() {
        TradeRequest req = new TradeRequest(
                "ABC-20260130-0003", 3L, 7L, "EQUITY", "BUY",
                BigDecimal.ONE, BigDecimal.ONE, LocalDate.of(2026, 1, 29));

        assertThat(mapper.toEntity(req).getStatus()).isEqualTo(new Trade().getStatus());
    }

    @Test
    @DisplayName("generated implementation is a Spring component")
    void generatedImplIsSpringComponent() {
        assertThat(TradeMapperImpl.class.getAnnotation(org.springframework.stereotype.Component.class))
                .isNotNull();
    }
}
