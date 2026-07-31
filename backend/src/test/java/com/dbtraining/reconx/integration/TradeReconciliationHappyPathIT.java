package com.dbtraining.reconx.integration;

import com.dbtraining.reconx.service.ReconciliationEngine;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * ============================================================================
 * TICKET-ADV143 — Integration test: end-to-end happy path
 *
 * WHAT:    Drives one message all the way through the real pipeline:
 *          POST /v1/trades (create, no event — TradeService.create only
 *          bumps metrics) -> PATCH /v1/trades/{id}/status ->
 *          TradeService.updateStatus -> TradeEventProducer publishes
 *          TRADE_UPDATED to `trade-events` -> a real Kafka broker ->
 *          ReconciliationConsumer (groupId=recon-service) consumes it ->
 *          ReconciliationEngine.scheduleRecon(tradeRef).
 * HOW:     Real Postgres via Testcontainers (@ServiceConnection) + an
 *          in-process @EmbeddedKafka broker rather than a Testcontainers
 *          Kafka container — the two consumer factories in
 *          KafkaConsumerFactoryConfig are hand-built from raw
 *          KafkaProperties (not Boot's autoconfigured, connection-details-
 *          aware ConsumerFactory), so @ServiceConnection's KafkaConnection
 *          Details bean would be invisible to them anyway; @EmbeddedKafka
 *          sets the spring.embedded.kafka.brokers system property, which
 *          @TestPropertySource feeds straight into the real
 *          spring.kafka.bootstrap-servers property both factories read.
 *          ReconciliationEngine is wrapped with @MockitoSpyBean so
 *          scheduleRecon's real (log-only) body still runs, but the call
 *          itself becomes observable. Mockito's verify(..., timeout(ms))
 *          polls rather than asserting immediately, which is required here
 *          — consumption is asynchronous relative to the HTTP response.
 * WHY:     Unit tests already cover TradeService and ReconciliationConsumer
 *          in isolation; neither proves the wiring between them (topic
 *          names, container factory, groupId, JSON (de)serialization
 *          config) actually lines up end-to-end on a real broker.
 * OBSERVE: A failure here with a timeout (rather than a wrong-argument
 *          assertion) usually means the consumer never started — check
 *          tradeEventListenerContainerFactory and the `recon-service`
 *          groupId first.
 * ============================================================================
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = {"trade-events", "system-alerts"})
@TestPropertySource(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
class TradeReconciliationHappyPathIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @LocalServerPort
    int port;

    @Autowired
    @MockitoSpyBean
    ReconciliationEngine reconciliationEngine;

    RestTemplate http = new RestTemplate(new JdkClientHttpRequestFactory());

    @Test
    void patchingTradeStatusPublishesAnEventThatTriggersReconciliation() {
        String token = loginAsAdmin();

        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        String tradeRef = "HAP-20260401-0001";
        var createBody = """
                {"tradeRef":"%s","instrumentId":1,"counterpartyId":1,
                 "assetClass":"EQUITY","side":"BUY",
                 "quantity":50.0,"price":100.0,"tradeDate":"2026-04-01"}
                """.formatted(tradeRef);

        var createResp = http.exchange(
                "http://localhost:" + port + "/api/v1/trades",
                HttpMethod.POST, new HttpEntity<>(createBody, headers), JsonNode.class);
        Assertions.assertEquals(HttpStatus.CREATED, createResp.getStatusCode());
        long tradeId = createResp.getBody().get("id").asLong();

        var statusBody = """
                {"status":"MATCHED"}
                """;
        var statusResp = http.exchange(
                "http://localhost:" + port + "/api/v1/trades/" + tradeId + "/status",
                HttpMethod.PATCH, new HttpEntity<>(statusBody, headers), JsonNode.class);
        Assertions.assertEquals(HttpStatus.OK, statusResp.getStatusCode());

        verify(reconciliationEngine, timeout(10_000)).scheduleRecon(tradeRef);
    }

    private String loginAsAdmin() {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        var loginBody = """
                {"email":"admin@db.com","password":"admin123"}
                """;
        var resp = http.postForEntity(
                "http://localhost:" + port + "/api/auth/login",
                new HttpEntity<>(loginBody, headers), JsonNode.class);
        Assertions.assertEquals(HttpStatus.OK, resp.getStatusCode());
        return resp.getBody().get("token").asText();
    }
}
