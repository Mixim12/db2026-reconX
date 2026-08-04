package com.dbtraining.reconx.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TICKET-ADV152 — every healthcheck works in isolation.
 *
 * <p>Acceptance criteria covered:
 * <ul>
 *   <li>every service defines its own {@code healthcheck:} block — no service
 *       relies on {@code depends_on} alone</li>
 *   <li>the retry budget of each check fits the window the ticket allows:
 *       postgres 10 s, kafka 30 s, backend 60 s</li>
 *   <li>the checks probe the service itself, not another container</li>
 * </ul>
 */
class ComposeHealthcheckConfigTest {

    private static Map<String, Object> healthcheck(String service) {
        Map<String, Object> block = DeploymentFiles.mapAt(ComposeFile.service(service), "healthcheck");
        assertThat(block).as("healthcheck block of service '%s'", service).isNotEmpty();
        return block;
    }

    private static Duration duration(Object value) {
        String text = String.valueOf(value).trim();
        return Duration.parse("PT" + text.replace("m", "M").replace("s", "S"));
    }

    /**
     * Worst-case time before docker gives up: start_period is a grace window in
     * which failures do not count, then every retry costs one interval.
     */
    private static Duration budget(Map<String, Object> healthcheck) {
        Duration startPeriod = healthcheck.containsKey("start_period")
                ? duration(healthcheck.get("start_period"))
                : Duration.ZERO;
        Duration interval = duration(healthcheck.get("interval"));
        int retries = ((Number) healthcheck.get("retries")).intValue();
        return startPeriod.plus(interval.multipliedBy(retries));
    }

    @ParameterizedTest
    @ValueSource(strings = {"postgres", "zookeeper", "kafka", "backend", "frontend", "prometheus",
            "grafana", "kafdrop"})
    void everyServiceDefinesItsOwnHealthcheck(String service) {
        Map<String, Object> healthcheck = healthcheck(service);

        assertThat(healthcheck).containsKeys("test", "interval", "timeout", "retries");
        assertThat(DeploymentFiles.listAt(healthcheck, "test")).isNotEmpty();
    }

    @Test
    void healthcheckedServicesCoverTheWholeStack() {
        assertThat(ComposeFile.services().keySet()).containsExactlyInAnyOrderElementsOf(ComposeFile.ALL_SERVICES);
    }

    @ParameterizedTest
    @CsvSource({
            "postgres, 10",
            "kafka,    30",
            "backend,  60"
    })
    void checkReachesHealthyWithinTheTicketBudget(String service, int allowedSeconds) {
        Map<String, Object> healthcheck = healthcheck(service);

        assertThat(duration(healthcheck.get("interval")))
                .as("%s poll interval must fit inside a %ds window", service, allowedSeconds)
                .isLessThanOrEqualTo(Duration.ofSeconds(allowedSeconds));
        assertThat(budget(healthcheck))
                .as("%s retry budget must outlast its %ds start-up window", service, allowedSeconds)
                .isGreaterThanOrEqualTo(Duration.ofSeconds(allowedSeconds));
    }

    @Test
    void backendCheckProbesItsOwnActuatorHealthEndpoint() {
        String test = String.join(" ", DeploymentFiles.listAt(healthcheck("backend"), "test").stream()
                .map(String::valueOf)
                .toList());

        assertThat(test).contains("/api/actuator/health");
        assertThat(test).doesNotContain("backend:8080");
    }

    @Test
    void postgresCheckUsesPgIsreadyWithTheApplicationCredentials() {
        String test = String.join(" ", DeploymentFiles.listAt(healthcheck("postgres"), "test").stream()
                .map(String::valueOf)
                .toList());

        assertThat(test).contains("pg_isready").contains("reconx_user").contains("reconx");
    }

    @Test
    void slowStartingServicesGetAStartPeriod() {
        List<String> slowServices = List.of("kafka", "backend");

        for (String service : slowServices) {
            assertThat(healthcheck(service))
                    .as("healthcheck of '%s'", service)
                    .containsKey("start_period");
        }
    }
}
