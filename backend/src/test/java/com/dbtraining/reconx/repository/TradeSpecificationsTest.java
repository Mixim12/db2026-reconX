package com.dbtraining.reconx.repository;

import com.dbtraining.reconx.repository.entity.Counterparty;
import com.dbtraining.reconx.repository.entity.Instrument;
import com.dbtraining.reconx.repository.entity.Trade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.time.LocalDate;
import java.util.List;

import static com.dbtraining.reconx.repository.TradeSpecifications.forCounterparty;
import static com.dbtraining.reconx.repository.TradeSpecifications.hasStatus;
import static com.dbtraining.reconx.repository.TradeSpecifications.refLike;
import static com.dbtraining.reconx.repository.TradeSpecifications.tradeDateBetween;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * TICKET-ADV056 — composable Specification factories.
 *
 * Each factory is exercised independently (that is the point of Specifications)
 * and then composed with Specification.where(...).and(...) to prove the
 * null-argument short-circuit really is a no-op rather than a false predicate.
 */
@DataJpaTest(properties = {
        "spring.liquibase.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class TradeSpecificationsTest {

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
        Long instrumentId = TradeTestData.insertInstrument(em, "BP.L", "BP plc");
        acmeId = TradeTestData.insertCounterparty(em, "ACME Capital", "LEI-ACME-0001");
        globexId = TradeTestData.insertCounterparty(em, "Globex Bank", "LEI-GLOBEX-002");

        Instrument instrument = em.find(Instrument.class, instrumentId);
        Counterparty acme = em.find(Counterparty.class, acmeId);
        Counterparty globex = em.find(Counterparty.class, globexId);

        TradeTestData.persistTrade(em, "TRD-0001", instrument, acme, LocalDate.of(2026, 1, 10), "PENDING");
        TradeTestData.persistTrade(em, "TRD-0002", instrument, globex, LocalDate.of(2026, 1, 20), "SETTLED");
        TradeTestData.persistTrade(em, "ALT-0003", instrument, acme, LocalDate.of(2026, 2, 15), "SETTLED");

        em.flush();
        em.clear();
    }

    private List<String> refsMatching(Specification<Trade> spec) {
        return tradeRepository.findAll(spec).stream().map(Trade::getTradeRef).toList();
    }

    @Test
    @DisplayName("TradeSpecifications is a final class with a private constructor")
    void isAFinalUtilityClass() throws Exception {
        assertThat(Modifier.isFinal(TradeSpecifications.class.getModifiers())).isTrue();

        Constructor<?>[] constructors = TradeSpecifications.class.getDeclaredConstructors();
        assertThat(constructors).hasSize(1);
        assertThat(Modifier.isPrivate(constructors[0].getModifiers())).isTrue();
    }

    @Test
    @DisplayName("tradeDateBetween(null, null) is a no-op and matches every trade")
    void tradeDateBetweenBothNullIsNoOp() {
        assertThat(refsMatching(tradeDateBetween(null, null)))
                .containsExactlyInAnyOrder("TRD-0001", "TRD-0002", "ALT-0003");
    }

    @Test
    @DisplayName("tradeDateBetween(from, to) filters on both bounds inclusively")
    void tradeDateBetweenBothBounds() {
        assertThat(refsMatching(tradeDateBetween(JAN_01, JAN_31)))
                .containsExactlyInAnyOrder("TRD-0001", "TRD-0002");
    }

    @Test
    @DisplayName("tradeDateBetween with only a lower bound keeps everything on or after it")
    void tradeDateBetweenOpenEnded() {
        assertThat(refsMatching(tradeDateBetween(LocalDate.of(2026, 1, 20), null)))
                .containsExactlyInAnyOrder("TRD-0002", "ALT-0003");
    }

    @Test
    @DisplayName("tradeDateBetween with only an upper bound keeps everything on or before it")
    void tradeDateBetweenOpenStarted() {
        assertThat(refsMatching(tradeDateBetween(null, LocalDate.of(2026, 1, 10))))
                .containsExactly("TRD-0001");
    }

    @Test
    @DisplayName("hasStatus(null) is a no-op; hasStatus(value) filters")
    void hasStatusFiltersAndShortCircuits() {
        assertThat(refsMatching(hasStatus(null))).hasSize(3);
        assertThat(refsMatching(hasStatus("SETTLED"))).containsExactlyInAnyOrder("TRD-0002", "ALT-0003");
    }

    @Test
    @DisplayName("hasStatus treats a blank status as no filter")
    void hasStatusIgnoresBlank() {
        assertThat(refsMatching(hasStatus("   "))).hasSize(3);
    }

    @Test
    @DisplayName("forCounterparty(null) is a no-op; forCounterparty(id) filters on the relation")
    void forCounterpartyFiltersAndShortCircuits() {
        assertThat(refsMatching(forCounterparty(null))).hasSize(3);
        assertThat(refsMatching(forCounterparty(globexId))).containsExactly("TRD-0002");
        assertThat(refsMatching(forCounterparty(acmeId))).containsExactlyInAnyOrder("TRD-0001", "ALT-0003");
    }

    @Test
    @DisplayName("refLike(null/blank) is a no-op; refLike(prefix) matches on prefix")
    void refLikeFiltersAndShortCircuits() {
        assertThat(refsMatching(refLike(null))).hasSize(3);
        assertThat(refsMatching(refLike(""))).hasSize(3);
        assertThat(refsMatching(refLike("  "))).hasSize(3);
        assertThat(refsMatching(refLike("TRD"))).containsExactlyInAnyOrder("TRD-0001", "TRD-0002");
        assertThat(refsMatching(refLike("ALT"))).containsExactly("ALT-0003");
    }

    @Test
    @DisplayName("composing with only a date range supplied ignores the other three filters")
    void composedWithOnlyDateRange() {
        Specification<Trade> spec = Specification.where(tradeDateBetween(JAN_01, JAN_31))
                .and(hasStatus(null))
                .and(forCounterparty(null))
                .and(refLike(null));

        assertThat(refsMatching(spec)).containsExactlyInAnyOrder("TRD-0001", "TRD-0002");
    }

    @Test
    @DisplayName("composing all four filters ANDs every predicate together")
    void composedWithAllFourFilters() {
        Specification<Trade> matching = Specification.where(tradeDateBetween(JAN_01, JAN_31))
                .and(hasStatus("SETTLED"))
                .and(forCounterparty(globexId))
                .and(refLike("TRD"));

        Specification<Trade> conflicting = Specification.where(tradeDateBetween(JAN_01, JAN_31))
                .and(hasStatus("SETTLED"))
                .and(forCounterparty(acmeId))
                .and(refLike("TRD"));

        assertThat(refsMatching(matching)).containsExactly("TRD-0002");
        assertThat(refsMatching(conflicting)).isEmpty();
    }

    @Test
    @DisplayName("Specification.allOf composes identically to where().and() — the form TradeService uses")
    void allOfComposesLikeWhereAnd() {
        Specification<Trade> viaAllOf = Specification.allOf(
                tradeDateBetween(JAN_01, JAN_31), hasStatus("SETTLED"), forCounterparty(globexId));
        Specification<Trade> viaWhere = Specification.where(tradeDateBetween(JAN_01, JAN_31))
                .and(hasStatus("SETTLED"))
                .and(forCounterparty(globexId));

        assertThat(refsMatching(viaAllOf)).containsExactly("TRD-0002");
        assertThat(refsMatching(viaAllOf)).isEqualTo(refsMatching(viaWhere));
    }

    @Test
    @DisplayName("Specification.allOf with every filter null matches every trade")
    void allOfWithNoFiltersMatchesEverything() {
        Specification<Trade> spec = Specification.allOf(
                tradeDateBetween(null, null), hasStatus(null), forCounterparty(null), refLike(null));

        assertThat(refsMatching(spec)).hasSize(3);
    }

    @Test
    @DisplayName("a composed Specification works with a Pageable through findAll")
    void composedSpecificationIsPageable() {
        Specification<Trade> spec = Specification.where(tradeDateBetween(JAN_01, JAN_31));

        Page<Trade> page = tradeRepository.findAll(spec, PageRequest.of(0, 1));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getTotalElements()).isEqualTo(2);
    }
}
