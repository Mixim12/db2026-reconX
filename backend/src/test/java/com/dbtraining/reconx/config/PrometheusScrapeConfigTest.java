package com.dbtraining.reconx.config;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TICKET-ADV149 — Prometheus scrape config.
 *
 * <p>Acceptance criteria covered:
 * <ul>
 *   <li>prometheus.yml declares scrape jobs for the backend and Kafka</li>
 *   <li>the backend job targets the compose service name on the actuator path</li>
 *   <li>no job scrapes {@code localhost:8080}</li>
 *   <li>the file is mounted read-only into the prometheus container</li>
 * </ul>
 */
class PrometheusScrapeConfigTest {

    private static final String PROMETHEUS_YML = "monitoring/prometheus/prometheus.yml";

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> scrapeConfigs() {
        Map<String, Object> root = DeploymentFiles.loadYaml(PROMETHEUS_YML);
        return (List<Map<String, Object>>) (List<?>) DeploymentFiles.listAt(root, "scrape_configs");
    }

    private Map<String, Object> job(String name) {
        return scrapeConfigs().stream()
                .filter(job -> name.equals(job.get("job_name")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No scrape job named '" + name + "' in " + PROMETHEUS_YML));
    }

    @SuppressWarnings("unchecked")
    private List<String> targetsOf(Map<String, Object> job) {
        return ((List<Map<String, Object>>) job.get("static_configs")).stream()
                .flatMap(sc -> ((List<String>) sc.get("targets")).stream())
                .toList();
    }

    @Test
    void backendJob_scrapesActuatorPrometheusEndpointOnTheComposeServiceName() {
        Map<String, Object> backend = job("reconx-backend");

        assertThat(backend.get("metrics_path")).isEqualTo("/api/actuator/prometheus");
        assertThat(targetsOf(backend)).containsExactly("backend:8080");
    }

    @Test
    void kafkaJob_isDeclaredAndTargetsTheKafkaServiceName() {
        Map<String, Object> kafka = job("kafka-jmx");

        assertThat(targetsOf(kafka))
                .isNotEmpty()
                .allSatisfy(target -> assertThat(target).startsWith("kafka:"));
    }

    @Test
    void noJobScrapesLocalhostOnTheBackendPort() {
        List<String> allTargets = scrapeConfigs().stream()
                .flatMap(job -> targetsOf(job).stream())
                .toList();

        assertThat(allTargets).doesNotContain("localhost:8080", "127.0.0.1:8080");
    }

    @Test
    void alertRulesAreLoadedAlongsideTheScrapeConfig() {
        Map<String, Object> root = DeploymentFiles.loadYaml(PROMETHEUS_YML);

        assertThat(DeploymentFiles.listAt(root, "rule_files")).contains("alerts.yml");
        assertThat(DeploymentFiles.mapAt(root, "global")).containsEntry("scrape_interval", "10s");
    }

    @Test
    void scrapeConfigIsMountedReadOnlyIntoThePrometheusContainer() {
        Map<String, Object> prometheus = ComposeFile.service("prometheus");

        assertThat(DeploymentFiles.listAt(prometheus, "volumes"))
                .contains("./monitoring/prometheus/prometheus.yml:/etc/prometheus/prometheus.yml:ro");
    }
}
