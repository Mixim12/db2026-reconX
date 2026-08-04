package com.dbtraining.reconx.config;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TICKET-ADV150 — Grafana provisioning (datasource + dashboards).
 *
 * <p>Acceptance criteria covered:
 * <ul>
 *   <li>the datasource YAML declares Prometheus as the default datasource</li>
 *   <li>a dashboard provider drops the dashboards into a "ReconX" folder, so no
 *       manual import is needed after {@code docker compose up}</li>
 *   <li>every panel's datasource UID matches the provisioned datasource UID —
 *       the mismatch that makes every panel render "No data"</li>
 *   <li>the provisioning tree is mounted into the grafana container</li>
 * </ul>
 */
class GrafanaProvisioningConfigTest {

    private static final String DATASOURCE_YML =
            "monitoring/grafana/provisioning/datasources/prometheus.yml";
    private static final String PROVIDER_YML =
            "monitoring/grafana/provisioning/dashboards/reconx.yml";
    private static final String DASHBOARD_JSON =
            "monitoring/grafana/provisioning/dashboards/reconx-overview.json";

    @SuppressWarnings("unchecked")
    private Map<String, Object> prometheusDatasource() {
        List<Object> datasources =
                DeploymentFiles.listAt(DeploymentFiles.loadYaml(DATASOURCE_YML), "datasources");
        return (Map<String, Object>) datasources.getFirst();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> dashboardProvider() {
        List<Object> providers =
                DeploymentFiles.listAt(DeploymentFiles.loadYaml(PROVIDER_YML), "providers");
        return (Map<String, Object>) providers.getFirst();
    }

    @Test
    void datasource_declaresPrometheusAsTheDefault() {
        Map<String, Object> datasource = prometheusDatasource();

        assertThat(DeploymentFiles.loadYaml(DATASOURCE_YML)).containsEntry("apiVersion", 1);
        assertThat(datasource)
                .containsEntry("type", "prometheus")
                .containsEntry("access", "proxy")
                .containsEntry("url", "http://prometheus:9090")
                .containsEntry("isDefault", true)
                .containsEntry("uid", "reconx-prometheus");
    }

    @Test
    void dashboardProvider_loadsFileDashboardsIntoTheReconXFolder() {
        Map<String, Object> provider = dashboardProvider();

        assertThat(provider)
                .containsEntry("type", "file")
                .containsEntry("folder", "ReconX");
        assertThat(DeploymentFiles.mapAt(provider, "options"))
                .containsEntry("path", "/etc/grafana/provisioning/dashboards");
    }

    /**
     * Every panel that queries data. "row" panels are collapsible section
     * headers — they carry no datasource and no targets by design.
     */
    private List<JsonNode> queryPanels() {
        List<JsonNode> panels = new ArrayList<>();
        collectQueryPanels(DeploymentFiles.loadJson(DASHBOARD_JSON).path("panels"), panels);
        return panels;
    }

    private void collectQueryPanels(JsonNode container, List<JsonNode> collected) {
        for (JsonNode panel : container) {
            if ("row".equals(panel.path("type").asText())) {
                collectQueryPanels(panel.path("panels"), collected);
            } else {
                collected.add(panel);
            }
        }
    }

    @Test
    void everyPanelUsesTheProvisionedDatasourceUid() {
        String provisionedUid = (String) prometheusDatasource().get("uid");

        List<String> panelUids = queryPanels().stream()
                .map(panel -> panel.path("datasource").path("uid").asText(null))
                .toList();

        assertThat(panelUids).isNotEmpty().allMatch(provisionedUid::equals);
    }

    @Test
    void everyPanelHasAQueryToRender() {
        for (JsonNode panel : queryPanels()) {
            assertThat(panel.path("targets")).as("targets of panel %s", panel.path("title").asText())
                    .isNotEmpty();
            for (JsonNode target : panel.path("targets")) {
                assertThat(target.path("expr").asText()).isNotBlank();
            }
        }
    }

    @Test
    void provisioningTreeIsMountedIntoTheGrafanaContainer() {
        Map<String, Object> grafana = ComposeFile.service("grafana");

        assertThat(DeploymentFiles.listAt(grafana, "volumes"))
                .contains("./monitoring/grafana/provisioning:/etc/grafana/provisioning:ro");
    }
}
