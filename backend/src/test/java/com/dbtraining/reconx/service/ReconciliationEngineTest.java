package com.dbtraining.reconx.service;

import com.dbtraining.reconx.dto.ReconResult;
import com.dbtraining.reconx.model.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TICKET-ADV040 / ADV041 / ADV042 — TDD: write the test FIRST, then the impl.
 */
class ReconciliationEngineTest {

    private final ReconciliationEngine engine = new ReconciliationEngine();

    @Test
    void testReconcile_exactMatch_returnsMatched() {
        // TODO(TICKET-ADV040): two identical EquityTrades + EXACT rule -> one ReconResult with status MATCHED.
        org.junit.jupiter.api.Assertions.fail("TICKET-ADV040 not implemented yet");
    }

    @Test
    void testReconcile_priceTolerance_withinThreshold() {
        // TODO(TICKET-ADV041): prices 100.00 vs 100.50 + PRICE_TOLERANCE_1PCT rule -> status MATCHED.
        org.junit.jupiter.api.Assertions.fail("TICKET-ADV041 not implemented yet");
    }

    @Test
    void testReconcile_missingCounterpartyTrade_returnsBreak() {
        // TODO(TICKET-ADV042): internal trade with no external counterpart -> status BREAK,
        //                     discrepancyType = "MISSING_EXTERNAL".
        org.junit.jupiter.api.Assertions.fail("TICKET-ADV042 not implemented yet");
    }

    // ---- TICKET-ADV047 edge cases -----------------------------------------

    @Test
    void testReconcile_emptyInternal_returnsEmpty() {
        // given / when
        List<ReconResult> out = engine.reconcile(List.of(), List.of(), ReconciliationRule.EXACT);

        // then
        assertThat(out).isEmpty();
    }

    @Test
    void testReconcile_singleInternalNoExternal_returnsBreakWithMissingExternalReason() {
        // given
        EquityTrade internal = equity("EQU-20260603-0020", "100.00", "1000");

        // when
        List<ReconResult> out = engine.reconcile(List.of(internal), List.of(), ReconciliationRule.EXACT);

        // then
        assertThat(out).hasSize(1);
        assertThat(out.get(0).status()).isEqualTo(ReconResult.Status.BREAK);
        // NOTE: engine's matchOne() names this reason "MISSING_EXTERNAL" (see
        // TICKET-ADV042's stub above) - aligning on that string rather than the
        // "MISSING_COUNTERPARTY_TRADE" wording used loosely in the guide prose.
        assertThat(out.get(0).discrepancyType()).isEqualTo("MISSING_EXTERNAL");
    }

    @Test
    void testReconcile_allMismatched_everyResultIsBreak() {
        // given
        EquityTrade i1 = equity("EQU-20260603-0010", "100.00", "1000");
        EquityTrade e1 = equity("EQU-20260603-0010", "150.00", "1000");
        EquityTrade i2 = equity("EQU-20260603-0011", "50.00", "500");
        EquityTrade e2 = equity("EQU-20260603-0011", "75.00", "500");
        EquityTrade i3 = equity("EQU-20260603-0012", "10.00", "100");
        EquityTrade e3 = equity("EQU-20260603-0012", "20.00", "100");

        // when
        List<ReconResult> out = engine.reconcile(
                List.of(i1, i2, i3), List.of(e1, e2, e3), ReconciliationRule.EXACT);

        // then
        // TODO(TICKET-ADV038 blocker): the reviewer asked for this assertion to go
        // through ReconSummaryCollector instead of manual filter/count, but neither
        // ReconSummaryCollector nor ReconSummary exist anywhere in the repo yet -
        // ADV038 is an even-numbered ticket outside this session's scope, so it is
        // not implemented here. Left as manual counting until ADV038 lands; revisit
        // then instead of guessing at ADV038's shape.
        assertThat(out).hasSize(3);
        long matched = out.stream().filter(r -> r.status() == ReconResult.Status.MATCHED).count();
        long broken  = out.stream().filter(r -> r.status() == ReconResult.Status.BREAK).count();
        assertThat(matched).isZero();
        assertThat(broken).isEqualTo(3);
    }

    private EquityTrade equity(String ref, String price, String qty) {
        return EquityTrade.builder()
                .tradeRef(TradeRef.of(ref))
                .instrumentSymbol("SAP.DE")
                .price(new BigDecimal(price))
                .quantity(new BigDecimal(qty))
                .currency("EUR").side(Side.BUY)
                .tradeDate(LocalDate.of(2026, 6, 3))
                .counterpartyId(1L)
                .build();
    }
}
