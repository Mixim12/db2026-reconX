package com.dbtraining.reconx.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ============================================================================
 * TICKET-ADV028 — equals and hashCode keyed on tradeRef
 *
 * Covers all four concrete trade types and the cross-type equality contract.
 * ============================================================================
 */
class TradeEqualityTest {

    // ── helpers ─────────────────────────────────────────────────────────

    private EquityTrade equity(String ref, BigDecimal qty, BigDecimal price) {
        return EquityTrade.builder()
                .tradeRef(TradeRef.of(ref))
                .instrumentSymbol("SAP.DE")
                .quantity(qty)
                .price(price)
                .currency("EUR")
                .side(Side.BUY)
                .tradeDate(LocalDate.of(2026, 6, 3))
                .counterpartyId(1L)
                .build();
    }

    private FXTrade fx(String ref, String ccy1, String ccy2) {
        return FXTrade.builder()
                .tradeRef(TradeRef.of(ref))
                .ccy1(ccy1)
                .ccy2(ccy2)
                .notionalCcy1(new BigDecimal("1000000"))
                .fxRate(new BigDecimal("1.10"))
                .side(Side.BUY)
                .tradeDate(LocalDate.of(2026, 6, 3))
                .counterpartyId(1L)
                .build();
    }

    private BondTrade bond(String ref, BigDecimal faceValue) {
        return BondTrade.builder()
                .tradeRef(TradeRef.of(ref))
                .isin("DE0001102580")
                .faceValue(faceValue)
                .couponRate(new BigDecimal("2.5"))
                .maturityDate(LocalDate.of(2030, 6, 3))
                .currency("EUR")
                .side(Side.BUY)
                .tradeDate(LocalDate.of(2026, 6, 3))
                .counterpartyId(1L)
                .build();
    }

    private DerivativeTrade derivative(String ref, BigDecimal strike) {
        return DerivativeTrade.builder()
                .tradeRef(TradeRef.of(ref))
                .underlying("SAP.DE")
                .strike(strike)
                .quantity(new BigDecimal("10"))
                .expiry(LocalDate.of(2027, 1, 1))
                .optionType(DerivativeTrade.OptionType.CALL)
                .currency("EUR")
                .side(Side.BUY)
                .tradeDate(LocalDate.of(2026, 6, 3))
                .counterpartyId(1L)
                .build();
    }

    // ── per-type equality ───────────────────────────────────────────────

    @Nested
    @DisplayName("EquityTrade equality")
    class EquityTradeEquality {

        @Test
        @DisplayName("same tradeRef, different fields → equal and same hashCode")
        void sameRef_equal() {
            EquityTrade t1 = equity("EQU-20260603-0001", new BigDecimal("100"), new BigDecimal("50"));
            EquityTrade t2 = equity("EQU-20260603-0001", new BigDecimal("999"), new BigDecimal("200"));
            assertThat(t1).isEqualTo(t2);
            assertThat(t1.hashCode()).isEqualTo(t2.hashCode());
        }

        @Test
        @DisplayName("different tradeRef → not equal")
        void differentRef_notEqual() {
            EquityTrade t1 = equity("EQU-20260603-0001", new BigDecimal("100"), new BigDecimal("50"));
            EquityTrade t2 = equity("EQU-20260603-0002", new BigDecimal("100"), new BigDecimal("50"));
            assertThat(t1).isNotEqualTo(t2);
        }
    }

    @Nested
    @DisplayName("FXTrade equality")
    class FXTradeEquality {

        @Test
        @DisplayName("same tradeRef, different currencies → equal and same hashCode")
        void sameRef_equal() {
            FXTrade t1 = fx("FXX-20260603-0001", "EUR", "USD");
            FXTrade t2 = fx("FXX-20260603-0001", "GBP", "JPY");
            assertThat(t1).isEqualTo(t2);
            assertThat(t1.hashCode()).isEqualTo(t2.hashCode());
        }

        @Test
        @DisplayName("different tradeRef → not equal")
        void differentRef_notEqual() {
            FXTrade t1 = fx("FXX-20260603-0001", "EUR", "USD");
            FXTrade t2 = fx("FXX-20260603-0002", "EUR", "USD");
            assertThat(t1).isNotEqualTo(t2);
        }
    }

    @Nested
    @DisplayName("BondTrade equality")
    class BondTradeEquality {

        @Test
        @DisplayName("same tradeRef, different faceValue → equal and same hashCode")
        void sameRef_equal() {
            BondTrade t1 = bond("BND-20260603-0001", new BigDecimal("100000"));
            BondTrade t2 = bond("BND-20260603-0001", new BigDecimal("500000"));
            assertThat(t1).isEqualTo(t2);
            assertThat(t1.hashCode()).isEqualTo(t2.hashCode());
        }

        @Test
        @DisplayName("different tradeRef → not equal")
        void differentRef_notEqual() {
            BondTrade t1 = bond("BND-20260603-0001", new BigDecimal("100000"));
            BondTrade t2 = bond("BND-20260603-0002", new BigDecimal("100000"));
            assertThat(t1).isNotEqualTo(t2);
        }
    }

    @Nested
    @DisplayName("DerivativeTrade equality")
    class DerivativeTradeEquality {

        @Test
        @DisplayName("same tradeRef, different strike → equal and same hashCode")
        void sameRef_equal() {
            DerivativeTrade t1 = derivative("DRV-20260603-0001", new BigDecimal("150"));
            DerivativeTrade t2 = derivative("DRV-20260603-0001", new BigDecimal("300"));
            assertThat(t1).isEqualTo(t2);
            assertThat(t1.hashCode()).isEqualTo(t2.hashCode());
        }

        @Test
        @DisplayName("different tradeRef → not equal")
        void differentRef_notEqual() {
            DerivativeTrade t1 = derivative("DRV-20260603-0001", new BigDecimal("150"));
            DerivativeTrade t2 = derivative("DRV-20260603-0002", new BigDecimal("150"));
            assertThat(t1).isNotEqualTo(t2);
        }
    }

    // ── cross-type equality ─────────────────────────────────────────────

    @Test
    @DisplayName("cross-type: EquityTrade ≠ FXTrade even with same tradeRef value")
    void crossType_equity_fx_notEqual() {
        // Both share the same tradeRef string, but instanceof checks differ
        EquityTrade eq = equity("EQU-20260603-0001", new BigDecimal("100"), new BigDecimal("50"));
        FXTrade fxt = fx("EQU-20260603-0001", "EUR", "USD");
        assertThat(eq).isNotEqualTo(fxt);
        assertThat(fxt).isNotEqualTo(eq);
    }

    @Test
    @DisplayName("cross-type: BondTrade ≠ DerivativeTrade even with same tradeRef value")
    void crossType_bond_derivative_notEqual() {
        BondTrade b = bond("BND-20260603-0001", new BigDecimal("100000"));
        DerivativeTrade d = derivative("BND-20260603-0001", new BigDecimal("150"));
        assertThat(b).isNotEqualTo(d);
        assertThat(d).isNotEqualTo(b);
    }

    // ── HashSet contract ────────────────────────────────────────────────

    @Test
    @DisplayName("HashSet<TradeType> with same tradeRef has size 1")
    void hashSet_sameRef_sizeOne() {
        EquityTrade t1 = equity("EQU-20260603-0001", new BigDecimal("100"), new BigDecimal("50"));
        EquityTrade t2 = equity("EQU-20260603-0001", new BigDecimal("999"), new BigDecimal("200"));

        HashSet<TradeType> set = new HashSet<>(List.of(t1, t2));
        assertThat(set).hasSize(1);
    }

    @Test
    @DisplayName("HashSet<TradeType> with different tradeRefs has size equal to number of trades")
    void hashSet_differentRefs_sizeMatches() {
        EquityTrade eq = equity("EQU-20260603-0001", new BigDecimal("100"), new BigDecimal("50"));
        FXTrade fxt = fx("FXX-20260603-0001", "EUR", "USD");
        BondTrade bnd = bond("BND-20260603-0001", new BigDecimal("100000"));
        DerivativeTrade drv = derivative("DRV-20260603-0001", new BigDecimal("150"));

        HashSet<TradeType> set = new HashSet<>(List.of(eq, fxt, bnd, drv));
        assertThat(set).hasSize(4);
    }

    // ── equals contract fundamentals ────────────────────────────────────

    @Test
    @DisplayName("equals: null returns false")
    void equals_null_returnsFalse() {
        EquityTrade t = equity("EQU-20260603-0001", new BigDecimal("100"), new BigDecimal("50"));
        assertThat(t.equals(null)).isFalse();
    }

    @Test
    @DisplayName("equals: identity returns true")
    void equals_identity_returnsTrue() {
        EquityTrade t = equity("EQU-20260603-0001", new BigDecimal("100"), new BigDecimal("50"));
        assertThat(t.equals(t)).isTrue();
    }

    @Test
    @DisplayName("equals: foreign type returns false")
    void equals_foreignType_returnsFalse() {
        EquityTrade t = equity("EQU-20260603-0001", new BigDecimal("100"), new BigDecimal("50"));
        assertThat(t.equals("not a trade")).isFalse();
    }
}
