package com.dbtraining.reconx.service;

import com.dbtraining.reconx.dto.ReconResult;
import com.dbtraining.reconx.model.EquityTrade;
import com.dbtraining.reconx.model.Side;
import com.dbtraining.reconx.model.TradeRef;
import com.dbtraining.reconx.model.TradeType;
import com.dbtraining.reconx.repository.CounterpartyRepository;
import com.dbtraining.reconx.repository.InstrumentRepository;
import com.dbtraining.reconx.repository.ReconResultRepository;
import com.dbtraining.reconx.repository.TradeRepository;
import com.dbtraining.reconx.repository.entity.Counterparty;
import com.dbtraining.reconx.repository.entity.Instrument;
import com.dbtraining.reconx.repository.entity.Trade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@Transactional
class ReconciliationIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("reconx")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        r.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        r.add("spring.liquibase.enabled", () -> "false");
        r.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @TestConfiguration
    static class TestBeansConfig {

        @Bean
        ReconResultRepository reconResultRepository() {
            return new InMemoryReconResultRepository();
        }

        @Bean
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder();
        }
    }

    static class InMemoryReconResultRepository implements ReconResultRepository {
        private final List<ReconResult> saved = Collections.synchronizedList(new ArrayList<>());

        @Override
        public ReconResult save(ReconResult result) {
            saved.add(result);
            return result;
        }

        List<ReconResult> findAll() {
            return List.copyOf(saved);
        }

        void clear() {
            saved.clear();
        }
    }

    @Autowired
    private TradeRepository tradeRepo;

    @Autowired
    private CounterpartyRepository counterpartyRepo;

    @Autowired
    private InstrumentRepository instrumentRepo;

    @Autowired
    private ReconciliationService reconciliationService;

    @Autowired
    private ReconResultRepository reconResultRepo;

    @Autowired
    private JdbcTemplate jdbc;

    private Counterparty testCounterparty;
    private Instrument testInstrument;

    @BeforeEach
    void seedReferenceData() {
        if (reconResultRepo instanceof InMemoryReconResultRepository inMem) {
            inMem.clear();
        }

        testCounterparty = new Counterparty();
        testCounterparty.setName("Test Counterparty");
        testCounterparty.setLeiCode("LEI-TEST-99999999");
        testCounterparty.setRegion("EMEA");
        testCounterparty = counterpartyRepo.save(testCounterparty);

        jdbc.update("""
                INSERT INTO instruments (symbol, name, asset_class, currency)
                VALUES ('TEST.XX', 'Test Instrument', 'EQUITY', 'EUR')
                ON CONFLICT (symbol) DO NOTHING
                """);
        testInstrument = instrumentRepo.findBySymbol("TEST.XX").orElseThrow();
    }

    @Test
    void containerIsRunning() {
        assertThat(postgres.isRunning()).isTrue();
    }

    @Test
    void insertedTradesAreReconciledAndPersisted() {
        Trade tradeA = buildTrade("EQU-20260615-0001");
        Trade tradeB = buildTrade("EQU-20260615-0002");
        tradeRepo.save(tradeA);
        tradeRepo.save(tradeB);

        TradeType domainInternal = toDomain("EQU-20260615-0001", tradeA);
        TradeType domainExternal = toDomain("EQU-20260615-0001", tradeB);

        List<ReconResult> results = reconciliationService.runRecon(
                List.of(domainInternal),
                List.of(domainExternal));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).status()).isEqualTo(ReconResult.Status.MATCHED);
        assertThat(results.get(0).tradeRef()).isEqualTo("EQU-20260615-0001");

        if (reconResultRepo instanceof InMemoryReconResultRepository inMem) {
            List<ReconResult> persisted = inMem.findAll();
            assertThat(persisted).hasSize(1);
            assertThat(persisted.get(0).status()).isEqualTo(ReconResult.Status.MATCHED);
            assertThat(persisted.get(0).tradeRef()).isEqualTo("EQU-20260615-0001");
        }
    }


    private Trade buildTrade(String tradeRef) {
        Trade t = new Trade();
        t.setTradeRef(tradeRef);
        t.setInstrument(testInstrument);
        t.setCounterparty(testCounterparty);
        t.setAssetClass("EQUITY");
        t.setSide("BUY");
        t.setQuantity(new BigDecimal("100"));
        t.setPrice(new BigDecimal("245.50"));
        t.setTradeDate(LocalDate.of(2026, 6, 15));
        t.setStatus("PENDING");
        return t;
    }

    private TradeType toDomain(String reconRef, Trade t) {
        return EquityTrade.builder()
                .tradeRef(TradeRef.of(reconRef))
                .instrumentSymbol(testInstrument.getSymbol())
                .price(t.getPrice())
                .quantity(t.getQuantity())
                .currency(testInstrument.getCurrency())
                .side(Side.BUY)
                .tradeDate(t.getTradeDate())
                .counterpartyId(testCounterparty.getId())
                .build();
    }
}
