package com.dbtraining.reconx.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TICKET-ADV019 — EquityTrade Builder tests
 * TICKET-ADV028 — equals/hashCode on EquityTrade
 */
class EquityTradeTest {

    // ── ADV019: happy-path build ────────────────────────────────────────

    @Test
    @DisplayName("ADV019: builder with all required fields produces a valid EquityTrade")
    void builder_buildsWhenAllRequiredPresent() {
        EquityTrade trade = sampleEquity("EQU-20260603-0001");

        assertThat(trade.tradeRef()).isEqualTo(TradeRef.of("EQU-20260603-0001"));
        assertThat(trade.instrumentSymbol()).isEqualTo("SAP.DE");
        assertThat(trade.quantity()).isEqualByComparingTo("100");
        assertThat(trade.price()).isEqualByComparingTo("100");
        assertThat(trade.currency()).isEqualTo(Currency.getInstance("EUR"));
        assertThat(trade.side()).isEqualTo(Side.BUY);
        assertThat(trade.tradeDate()).isEqualTo(LocalDate.of(2026, 6, 3));
        assertThat(trade.counterpartyId()).isEqualTo(1L);
        assertThat(trade.assetClass()).isEqualTo(TradeType.AssetClass.EQUITY);
        // notional = qty * price = 100 * 100 = 10000 EUR
        assertThat(trade.notional().amount()).isEqualByComparingTo("10000");
        assertThat(trade.notional().currency()).isEqualTo(Currency.getInstance("EUR"));
    }

    // ── ADV019: missing required fields ─────────────────────────────────

    @Test
    @DisplayName("ADV019: missing price throws NullPointerException mentioning 'price'")
    void builder_missingPrice_throws() {
        assertThatThrownBy(() ->
                EquityTrade.builder()
                        .tradeRef(TradeRef.of("EQU-20260603-0001"))
                        .instrumentSymbol("SAP.DE")
                        .quantity(new BigDecimal("100"))
                        // price deliberately omitted
                        .currency("EUR")
                        .side(Side.BUY)
                        .tradeDate(LocalDate.of(2026, 6, 3))
                        .counterpartyId(1L)
                        .build()
        ).isInstanceOf(NullPointerException.class)
         .hasMessageContaining("price");
    }

    @Test
    @DisplayName("ADV019: missing tradeRef throws NullPointerException mentioning 'tradeRef'")
    void builder_missingTradeRef_throws() {
        assertThatThrownBy(() ->
                EquityTrade.builder()
                        .instrumentSymbol("SAP.DE")
                        .quantity(new BigDecimal("100"))
                        .price(new BigDecimal("100"))
                        .currency("EUR")
                        .side(Side.BUY)
                        .tradeDate(LocalDate.of(2026, 6, 3))
                        .counterpartyId(1L)
                        .build()
        ).isInstanceOf(NullPointerException.class)
         .hasMessageContaining("tradeRef");
    }

    // ── ADV019: invariant validation ────────────────────────────────────

    @Test
    @DisplayName("ADV019: zero quantity is rejected")
    void builder_zeroQuantity_throws() {
        assertThatThrownBy(() ->
                EquityTrade.builder()
                        .tradeRef(TradeRef.of("EQU-20260603-0001"))
                        .instrumentSymbol("SAP.DE")
                        .quantity(BigDecimal.ZERO)
                        .price(new BigDecimal("100"))
                        .currency("EUR")
                        .side(Side.BUY)
                        .tradeDate(LocalDate.of(2026, 6, 3))
                        .counterpartyId(1L)
                        .build()
        ).isInstanceOf(IllegalStateException.class)
         .hasMessageContaining("quantity");
    }

    @Test
    @DisplayName("ADV019: negative price is rejected")
    void builder_negativePrice_throws() {
        assertThatThrownBy(() ->
                EquityTrade.builder()
                        .tradeRef(TradeRef.of("EQU-20260603-0001"))
                        .instrumentSymbol("SAP.DE")
                        .quantity(new BigDecimal("100"))
                        .price(new BigDecimal("-50"))
                        .currency("EUR")
                        .side(Side.BUY)
                        .tradeDate(LocalDate.of(2026, 6, 3))
                        .counterpartyId(1L)
                        .build()
        ).isInstanceOf(IllegalStateException.class)
         .hasMessageContaining("price");
    }

    // ── ADV019: structural constraints ──────────────────────────────────

    @Test
    @DisplayName("ADV019: EquityTrade is final")
    void equityTrade_isFinal() {
        assertThat(Modifier.isFinal(EquityTrade.class.getModifiers())).isTrue();
    }

    @Test
    @DisplayName("ADV019: EquityTrade has no public constructor")
    void equityTrade_noPublicConstructor() {
        assertThat(Arrays.stream(EquityTrade.class.getDeclaredConstructors())
                .noneMatch(c -> Modifier.isPublic(c.getModifiers()))).isTrue();
    }

    @Test
    @DisplayName("ADV019: Side enum has exactly BUY and SELL")
    void side_hasBuyAndSell() {
        assertThat(Side.values()).containsExactly(Side.BUY, Side.SELL);
    }

    // ── ADV028: equality / hashCode ─────────────────────────────────────

    @Test
    @DisplayName("ADV028: two EquityTrades with same tradeRef are equal and share hashCode")
    void equality_byTradeRef() {
        EquityTrade t1 = sampleEquity("EQU-20260603-0001");
        EquityTrade t2 = EquityTrade.builder()
                .tradeRef(TradeRef.of("EQU-20260603-0001"))
                .instrumentSymbol("VOW3.DE")          // different symbol
                .quantity(new BigDecimal("999"))        // different qty
                .price(new BigDecimal("50"))            // different price
                .currency("USD")                       // different ccy
                .side(Side.SELL)                       // different side
                .tradeDate(LocalDate.of(2026, 7, 1))   // different date
                .counterpartyId(99L)                   // different cpty
                .build();

        assertThat(t1).isEqualTo(t2);
        assertThat(t1.hashCode()).isEqualTo(t2.hashCode());

        EquityTrade t3 = sampleEquity("EQU-20260603-0002");
        assertThat(t1).isNotEqualTo(t3);
    }

    // ── helper ──────────────────────────────────────────────────────────

    private EquityTrade sampleEquity(String ref) {
        return EquityTrade.builder()
                .tradeRef(TradeRef.of(ref))
                .instrumentSymbol("SAP.DE")
                .quantity(new BigDecimal("100"))
                .price(new BigDecimal("100"))
                .currency("EUR").side(Side.BUY)
                .tradeDate(LocalDate.of(2026, 6, 3))
                .counterpartyId(1L).build();
    }
}
