package com.dbtraining.reconx.repository;

import com.dbtraining.reconx.repository.entity.Counterparty;
import com.dbtraining.reconx.repository.entity.Instrument;
import com.dbtraining.reconx.repository.entity.Trade;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Seed helpers shared by the TICKET-ADV055 / TICKET-ADV056 persistence tests.
 *
 * {@link Instrument} and {@link Counterparty} are read-only entities in this
 * codebase (no setters), so rows are inserted with native SQL against the
 * schema Hibernate generates from those entities.
 */
final class TradeTestData {

    private TradeTestData() {}

    /** Deterministic 12-character ISIN — Instrument.isin is VARCHAR(12) UNIQUE. */
    private static String isinFor(String symbol) {
        String digits = String.format("%08d", Math.floorMod(symbol.hashCode(), 100_000_000));
        return "GB00" + digits;
    }

    static Long insertInstrument(TestEntityManager em, String symbol, String name) {
        em.getEntityManager()
                .createNativeQuery("""
                        INSERT INTO instruments (symbol, name, asset_class, currency, isin)
                        VALUES (?, ?, 'EQUITY', 'GBP', ?)
                        """)
                .setParameter(1, symbol)
                .setParameter(2, name)
                .setParameter(3, isinFor(symbol))
                .executeUpdate();
        return ((Number) em.getEntityManager()
                .createNativeQuery("SELECT id FROM instruments WHERE symbol = ?")
                .setParameter(1, symbol)
                .getSingleResult()).longValue();
    }

    static Long insertCounterparty(TestEntityManager em, String name, String leiCode) {
        em.getEntityManager()
                .createNativeQuery("""
                        INSERT INTO counterparties (name, lei_code, region, created_at)
                        VALUES (?, ?, 'EMEA', CURRENT_TIMESTAMP)
                        """)
                .setParameter(1, name)
                .setParameter(2, leiCode)
                .executeUpdate();
        return ((Number) em.getEntityManager()
                .createNativeQuery("SELECT id FROM counterparties WHERE lei_code = ?")
                .setParameter(1, leiCode)
                .getSingleResult()).longValue();
    }

    static Trade persistTrade(TestEntityManager em,
                              String tradeRef,
                              Instrument instrument,
                              Counterparty counterparty,
                              LocalDate tradeDate,
                              String status) {
        Trade trade = new Trade();
        trade.setTradeRef(tradeRef);
        trade.setInstrument(instrument);
        trade.setCounterparty(counterparty);
        trade.setAssetClass("EQUITY");
        trade.setSide("BUY");
        trade.setQuantity(new BigDecimal("100.0000"));
        trade.setPrice(new BigDecimal("10.5000"));
        trade.setTradeDate(tradeDate);
        trade.setStatus(status);
        return em.persist(trade);
    }
}
