package com.dbtraining.reconx.service;

import com.dbtraining.reconx.config.JpaConfig;
import com.dbtraining.reconx.repository.entity.Counterparty;
import com.dbtraining.reconx.repository.entity.Instrument;
import com.dbtraining.reconx.repository.entity.Trade;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.liquibase.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
// JpaConfig carries @EnableJpaAuditing, which @DataJpaTest would otherwise filter
// out, leaving Trade.createdAt null against a NOT NULL column.
@Import({TradeHistoryService.class, JpaConfig.class})
// Envers writes audit rows when a transaction commits, and records one revision
// per transaction. The rollback-only transaction @DataJpaTest wraps around each
// test would therefore produce zero revisions, so transactions are driven
// explicitly below instead.
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TradeHistoryServiceTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private TradeHistoryService tradeHistoryService;

    @Test
    @DisplayName("Verify Envers tracks 4 revisions when a trade is inserted and updated 3 times")
    void testRevisionsForTrade() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        Long tradeId = tx.execute(status -> {
            Counterparty counterparty = new Counterparty();
            counterparty.setName("Test Counterparty");
            counterparty.setLeiCode("LEI-TEST-001");
            counterparty.setRegion("US");
            entityManager.persist(counterparty);

            Instrument instrument = new Instrument();
            instrument.setSymbol("TEST.L");
            instrument.setName("Test Instrument");
            instrument.setAssetClass("EQUITY");
            instrument.setCurrency("GBP");
            entityManager.persist(instrument);

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
            entityManager.persist(trade);
            entityManager.flush();

            return trade.getId();
        });

        updateInOwnTransaction(tx, tradeId, trade -> trade.setStatus("MATCHED"));
        updateInOwnTransaction(tx, tradeId, trade -> trade.setQuantity(BigDecimal.valueOf(200)));
        updateInOwnTransaction(tx, tradeId, trade -> trade.setStatus("SETTLED"));

        List<Number> revisions = tradeHistoryService.revisionsFor(tradeId);
        assertThat(revisions).hasSize(4);

        Trade firstRev = tradeHistoryService.snapshotAt(tradeId, revisions.get(0));
        assertThat(firstRev.getStatus()).isEqualTo("PENDING");

        Trade lastRev = tradeHistoryService.snapshotAt(tradeId, revisions.get(3));
        assertThat(lastRev.getStatus()).isEqualTo("SETTLED");
    }

    /** One committed transaction per mutation — one Envers revision each. */
    private void updateInOwnTransaction(TransactionTemplate tx, Long tradeId, TradeMutation mutation) {
        tx.executeWithoutResult(status -> {
            Trade trade = entityManager.find(Trade.class, tradeId);
            mutation.apply(trade);
            entityManager.flush();
        });
    }

    @FunctionalInterface
    private interface TradeMutation {
        void apply(Trade trade);
    }
}
