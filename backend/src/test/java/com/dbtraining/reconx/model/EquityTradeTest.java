package com.dbtraining.reconx.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EquityTradeTest {

    @Test
    void builder_buildsWhenAllRequiredPresent() {
        EquityTrade trade = sampleEquity("EQU-20260603-0001");
        assertThat(trade.tradeRef().value()).isEqualTo("EQU-20260603-0001");
        assertThat(trade.notional().amount()).isEqualByComparingTo(new BigDecimal("10000"));
        assertThat(trade.assetClass()).isEqualTo(TradeType.AssetClass.EQUITY);
    }

    @Test
    void builder_missingPrice_throws() {
        assertThatThrownBy(() -> EquityTrade.builder()
                .tradeRef(TradeRef.of("EQU-20260603-0001"))
                .instrumentSymbol("SAP.DE")
                .quantity(new BigDecimal("100"))
                .currency("EUR").side(Side.BUY)
                .tradeDate(LocalDate.of(2026, 6, 3))
                .counterpartyId(1L).build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("price");
    }

    @Test
    void equality_byTradeRef() {
        EquityTrade t1 = sampleEquity("EQU-20260603-0001");
        EquityTrade t2 = sampleEquity("EQU-20260603-0001");
        EquityTrade t3 = sampleEquity("EQU-20260603-0002");
        assertThat(t1).isEqualTo(t2);
        assertThat(t1.hashCode()).isEqualTo(t2.hashCode());
        assertThat(t1).isNotEqualTo(t3);
    }

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
