package com.dbtraining.reconx.service;

import com.dbtraining.reconx.repository.entity.Counterparty;
import com.dbtraining.reconx.repository.entity.Instrument;
import com.dbtraining.reconx.repository.entity.Trade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.liquibase.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import(TradeHistoryService.class)
class TradeHistoryServiceTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private TradeHistoryService tradeHistoryService;

    @Test
    @DisplayName("Verify Envers tracks 4 revisions when a trade is inserted and updated 3 times")
    void testRevisionsForTrade() {
        Counterparty counterparty = new Counterparty();
        counterparty.setName("Test Counterparty");
        counterparty.setLeiCode("LEI-TEST-001");
        counterparty.setRegion("US");
        counterparty = entityManager.persistAndFlush(counterparty);

        Instrument instrument = new Instrument();
        instrument.setSymbol("TEST.L");
        instrument.setName("Test Instrument");
        instrument.setAssetClass("EQUITY");
        instrument.setCurrency("GBP");
        instrument = entityManager.persistAndFlush(instrument);

        Trade trade = new Trade();
        trade.setTradeRef("TRD-AUD-001");
        trade.setCounterparty(counterparty);
        trade.setInstrument(instrument);
        trade.setAssetClass("EQUITY");
        trade.setSide("BUY");
        trade.setQuantity(BigDecimal.valueOf(100));
        trade.setPrice(BigDecimal.valueOf(50.0));
        trade.setTradeDate(LocalDate.now());
        trade.setStatus("PENDING");

        Trade saved = entityManager.persistAndFlush(trade);
        Long tradeId = saved.getId();

        saved.setStatus("MATCHED");
        entityManager.persistAndFlush(saved);

        saved.setQuantity(BigDecimal.valueOf(200));
        entityManager.persistAndFlush(saved);

        saved.setStatus("SETTLED");
        entityManager.persistAndFlush(saved);

        List<Number> revisions = tradeHistoryService.revisionsFor(tradeId);
        assertThat(revisions).hasSize(4);

        Trade firstRev = tradeHistoryService.snapshotAt(tradeId, revisions.get(0));
        assertThat(firstRev.getStatus()).isEqualTo("PENDING");

        Trade lastRev = tradeHistoryService.snapshotAt(tradeId, revisions.get(3));
        assertThat(lastRev.getStatus()).isEqualTo("SETTLED");
    }
}
