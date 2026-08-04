package com.dbtraining.reconx.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

/**
 * ============================================================================
 * TICKET-ADV097 — Performance test: 100 concurrent requests
 *
 * WHAT:    Fires 100 concurrent GET /v1/trades requests against a real,
 *          Postgres-backed Spring Boot instance and asserts every one of
 *          them completes with 200 OK inside a fixed time budget.
 * HOW:     A fixed thread pool submits 100 Callables, each issuing its own
 *          RestTemplate call (RestTemplate is not thread-safe to share
 *          state across, but stateless per-call use like this is fine).
 *          CountDownLatch is not needed — invokeAll() already blocks until
 *          every task finishes or the pool is shut down.
 * WHY:     Same JVM instance, real connection pool (HikariCP) and real
 *          Tomcat thread pool as production — this is the cheapest way to
 *          catch connection-pool exhaustion or thread-starvation regressions
 *          before they show up under the heavier k6 load test (ADV158).
 * OBSERVE: A failing assertion here (non-200 responses, or the budget blown)
 *          usually means HikariCP's maximum-pool-size or server.tomcat's
 *          thread limits need revisiting in application.yml.
 * ============================================================================
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class TradeListConcurrencyPerformanceIT {

    private static final int CONCURRENT_REQUESTS = 100;
    private static final Duration TIME_BUDGET = Duration.ofSeconds(30);

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @LocalServerPort
    int port;

    String token;

    @BeforeEach
    void loginAsAdmin() {
        RestTemplate setup = new RestTemplate(new JdkClientHttpRequestFactory());
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        var body = """
                {"email":"admin@db.com","password":"admin123"}
                """;
        var resp = setup.postForEntity(
                "http://localhost:" + port + "/api/auth/login",
                new HttpEntity<>(body, headers), JsonNode.class);
        Assertions.assertEquals(HttpStatus.OK, resp.getStatusCode());
        token = resp.getBody().get("token").asText();
    }

    @Test
    void handlesOneHundredConcurrentListRequests() throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
        AtomicInteger successCount = new AtomicInteger();

        List<Callable<HttpStatusCode>> requests = IntStream.range(0, CONCURRENT_REQUESTS)
                .<Callable<HttpStatusCode>>mapToObj(i -> () -> {
                    RestTemplate http = new RestTemplate(new JdkClientHttpRequestFactory());
                    var headers = new HttpHeaders();
                    headers.setBearerAuth(token);
                    var resp = http.exchange(
                            "http://localhost:" + port + "/api/v1/trades",
                            HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
                    if (resp.getStatusCode() == HttpStatus.OK) {
                        successCount.incrementAndGet();
                    }
                    return resp.getStatusCode();
                })
                .toList();

        Instant start = Instant.now();
        List<Future<HttpStatusCode>> futures = pool.invokeAll(
                requests, TIME_BUDGET.toSeconds(), TimeUnit.SECONDS);
        Duration elapsed = Duration.between(start, Instant.now());
        pool.shutdown();

        Assertions.assertTrue(futures.stream().noneMatch(Future::isCancelled),
                "at least one request did not complete within the " + TIME_BUDGET.toSeconds() + "s budget");
        Assertions.assertEquals(CONCURRENT_REQUESTS, successCount.get(),
                "not every concurrent request returned 200 OK");
        Assertions.assertTrue(elapsed.compareTo(TIME_BUDGET) <= 0,
                "100 concurrent requests took " + elapsed.toSeconds() + "s, budget was " + TIME_BUDGET.toSeconds() + "s");
    }
}
