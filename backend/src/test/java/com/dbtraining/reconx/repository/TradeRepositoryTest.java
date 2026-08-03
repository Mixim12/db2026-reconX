package com.dbtraining.reconx.repository;

import com.dbtraining.reconx.config.JpaConfig;
import com.dbtraining.reconx.repository.entity.Counterparty;
import com.dbtraining.reconx.repository.entity.Instrument;
import com.dbtraining.reconx.repository.entity.Trade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TICKET-ADV055 — TradeRepository derived query + JPQL filter query.
 *
 * Every assertion here maps to one of the ticket's acceptance criteria:
 *  - extends JpaRepository + JpaSpecificationExecutor
 *  - findByTradeRef(String) returns Optional<Trade>
 *  - findByFilters(from, to, status, counterpartyId, Pageable) returns Page<Trade>
 *    and honours the (:param IS NULL OR ...) optional-filter idiom
 */
@DataJpaTest(properties = {
        "spring.liquibase.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
// @DataJpaTest filters out application @Configuration classes, so the auditing
// listener behind Trade.createdAt / Trade.modifiedAt has to be imported explicitly.
@Import(JpaConfig.class)
class TradeRepositoryTest {

    @Autowired
    private TradeRepository tradeRepository;

    @Autowired
    private TestEntityManager em;

    private Long acmeId;
    private Long globexId;

    private static final LocalDate JAN_01 = LocalDate.of(2026, 1, 1);
    private static final LocalDate JAN_31 = LocalDate.of(2026, 1, 31);

    @BeforeEach
    void seed() {
        Long instrumentId = TradeTestData.insertInstrument(em, "VOD.L", "Vodafone Group");
        acmeId = TradeTestData.insertCounterparty(em, "ACME Capital", "LEI-ACME-0001");
        globexId = TradeTestData.insertCounterparty(em, "Globex Bank", "LEI-GLOBEX-002");

        Instrument instrument = em.find(Instrument.class, instrumentId);
        Counterparty acme = em.find(Counterparty.class, acmeId);
        Counterparty globex = em.find(Counterparty.class, globexId);

        // inside the January window
        TradeTestData.persistTrade(em, "TRD-0001", instrument, acme, LocalDate.of(2026, 1, 10), "PENDING");
        TradeTestData.persistTrade(em, "TRD-0002", instrument, globex, LocalDate.of(2026, 1, 20), "SETTLED");
        // outside the January window
        TradeTestData.persistTrade(em, "TRD-0003", instrument, acme, LocalDate.of(2026, 2, 15), "PENDING");

        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("extends both JpaRepository and JpaSpecificationExecutor")
    void extendsBothSpringDataInterfaces() {
        assertThat(JpaRepository.class).isAssignableFrom(TradeRepository.class);
        assertThat(JpaSpecificationExecutor.class).isAssignableFrom(TradeRepository.class);
    }

    @Test
    @DisplayName("findByTradeRef returns the matching trade")
    void findByTradeRefReturnsMatch() {
        Optional<Trade> found = tradeRepository.findByTradeRef("TRD-0002");

        assertThat(found).isPresent();
        assertThat(found.get().getTradeRef()).isEqualTo("TRD-0002");
    }

    @Test
    @DisplayName("findByTradeRef returns empty for an unknown ref")
    void findByTradeRefReturnsEmptyWhenMissing() {
        assertThat(tradeRepository.findByTradeRef("TRD-9999")).isEmpty();
    }

    @Test
    @DisplayName("findByFilters with no optional filters returns only trades inside the date range")
    void findByFiltersDateRangeOnly() {
        Page<Trade> page = tradeRepository.findByFilters(JAN_01, JAN_31, null, null, PageRequest.of(0, 10));

        assertThat(page.getContent())
                .extracting(Trade::getTradeRef)
                .containsExactlyInAnyOrder("TRD-0001", "TRD-0002");
        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("findByFilters narrows by status when a status is supplied")
    void findByFiltersAppliesStatus() {
        Page<Trade> page = tradeRepository.findByFilters(JAN_01, JAN_31, "SETTLED", null, PageRequest.of(0, 10));

        assertThat(page.getContent())
                .extracting(Trade::getTradeRef)
                .containsExactly("TRD-0002");
    }

    @Test
    @DisplayName("findByFilters narrows by counterparty when a counterpartyId is supplied")
    void findByFiltersAppliesCounterparty() {
        Page<Trade> page = tradeRepository.findByFilters(JAN_01, JAN_31, null, acmeId, PageRequest.of(0, 10));

        assertThat(page.getContent())
                .extracting(Trade::getTradeRef)
                .containsExactly("TRD-0001");
    }

    @Test
    @DisplayName("findByFilters combines status and counterparty filters")
    void findByFiltersCombinesOptionalFilters() {
        Page<Trade> matching =
                tradeRepository.findByFilters(JAN_01, JAN_31, "SETTLED", globexId, PageRequest.of(0, 10));
        Page<Trade> conflicting =
                tradeRepository.findByFilters(JAN_01, JAN_31, "SETTLED", acmeId, PageRequest.of(0, 10));

        assertThat(matching.getContent()).extracting(Trade::getTradeRef).containsExactly("TRD-0002");
        assertThat(conflicting.getContent()).isEmpty();
    }

    @Test
    @DisplayName("findByFilters honours the Pageable it is given")
    void findByFiltersIsPaginated() {
        Page<Trade> firstPage = tradeRepository.findByFilters(JAN_01, JAN_31, null, null, PageRequest.of(0, 1));

        assertThat(firstPage.getContent()).hasSize(1);
        assertThat(firstPage.getTotalElements()).isEqualTo(2);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
    }

    @Test
    @DisplayName("findByFilters excludes trades outside the date range")
    void findByFiltersExcludesOutOfRange() {
        Page<Trade> february = tradeRepository.findByFilters(
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28), null, null, PageRequest.of(0, 10));

        assertThat(february.getContent()).extracting(Trade::getTradeRef).containsExactly("TRD-0003");
    }

    @Test
    @DisplayName("saved trades round-trip through the repository")
    void savesAndReloads() {
        Instrument instrument = tradeRepository.findByTradeRef("TRD-0001").orElseThrow().getInstrument();
        Counterparty counterparty = em.find(Counterparty.class, acmeId);

        Trade trade = new Trade();
        trade.setTradeRef("TRD-0100");
        trade.setInstrument(instrument);
        trade.setCounterparty(counterparty);
        trade.setAssetClass("EQUITY");
        trade.setSide("BUY");
        trade.setQuantity(new BigDecimal("100.0000"));
        trade.setPrice(new BigDecimal("12.3400"));
        trade.setTradeDate(LocalDate.of(2026, 1, 15));

        tradeRepository.saveAndFlush(trade);

        assertThat(tradeRepository.findByTradeRef("TRD-0100")).isPresent();
    }
}
