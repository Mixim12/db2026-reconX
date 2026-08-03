package com.dbtraining.reconx.observability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public class ObservabilityConfigTest {

    @Test
    public void testAlertsConfigHasKafkaDlqMessages() throws IOException {
        File alertsFile = new File("../monitoring/prometheus/alerts.yml");
        assertThat(alertsFile).exists();

        ObjectMapper yamlMapper = new YAMLMapper();
        JsonNode rootNode = yamlMapper.readTree(alertsFile);
        
        JsonNode groups = rootNode.get("groups");
        assertThat(groups).isNotNull();

        boolean found = false;
        for (JsonNode group : groups) {
            JsonNode rules = group.get("rules");
            if (rules != null) {
                for (JsonNode rule : rules) {
                    if (rule.has("alert") && "KafkaDlqMessages".equals(rule.get("alert").asText())) {
                        found = true;
                        String expr = rule.get("expr").asText();
                        assertThat(expr).contains("kafka_consumer_records_consumed_total");
                        assertThat(expr).contains("topic=\"trade-events-dlq\"");
                        assertThat(expr).contains(">");
                        assertThat(expr).contains("0");
                        
                        assertThat(rule.get("labels").get("severity").asText()).isEqualTo("critical");
                        assertThat(rule.get("annotations").get("description").asText()).contains("/api/v1/admin/dlq");
                        assertThat(rule.get("for").asText()).isEqualTo("1m");
                    }
                }
            }
        }
        assertThat(found).as("KafkaDlqMessages alert rule must be present").isTrue();
    }

    @Test
    public void testGrafanaDashboardHasKafkaPanels() throws IOException {
        File dashboardFile = new File("../monitoring/grafana/provisioning/dashboards/reconx-overview.json");
        assertThat(dashboardFile).exists();

        ObjectMapper mapper = new ObjectMapper();
        JsonNode rootNode = mapper.readTree(dashboardFile);
        
        JsonNode panels = rootNode.get("panels");
        assertThat(panels).isNotNull();

        boolean foundKafkaRow = false;
        boolean foundConsumerLagPanel = false;
        boolean foundThroughputPanel = false;
        boolean foundDlqPanel = false;

        for (JsonNode panel : panels) {
            String type = panel.has("type") ? panel.get("type").asText() : "";
            String title = panel.has("title") ? panel.get("title").asText() : "";

            if ("row".equals(type) && title.toLowerCase().contains("kafka")) {
                foundKafkaRow = true;
            } else if ("timeseries".equals(type) && "Consumer lag by topic".equals(title)) {
                foundConsumerLagPanel = true;
                String expr = panel.get("targets").get(0).get("expr").asText();
                assertThat(expr).isEqualTo("sum by (topic) (kafka_consumer_records_lag)");
                
                // Assert thresholds
                JsonNode steps = panel.path("fieldConfig").path("defaults").path("thresholds").path("steps");
                assertThat(steps.isArray()).isTrue();
                boolean foundYellow = false;
                boolean foundRed = false;
                for (JsonNode step : steps) {
                    String color = step.path("color").asText();
                    if ("yellow".equals(color)) {
                        assertThat(step.path("value").asInt()).isEqualTo(100);
                        foundYellow = true;
                    } else if ("red".equals(color)) {
                        assertThat(step.path("value").asInt()).isEqualTo(1000);
                        foundRed = true;
                    }
                }
                assertThat(foundYellow).as("Yellow threshold at 100").isTrue();
                assertThat(foundRed).as("Red threshold at 1000").isTrue();

            } else if ("timeseries".equals(type) && "Throughput: produced vs consumed".equals(title)) {
                foundThroughputPanel = true;
                JsonNode targets = panel.get("targets");
                assertThat(targets.size()).isGreaterThanOrEqualTo(2);
                
                boolean foundConsumed = false;
                boolean foundProduced = false;
                for (JsonNode target : targets) {
                    String legend = target.path("legendFormat").asText();
                    String expr = target.path("expr").asText();
                    if ("consumed".equals(legend)) {
                        assertThat(expr).isEqualTo("sum(rate(kafka_consumer_records_consumed_total[1m]))");
                        foundConsumed = true;
                    } else if ("produced".equals(legend)) {
                        assertThat(expr).isEqualTo("sum(rate(kafka_producer_record_send_total[1m]))");
                        foundProduced = true;
                    }
                }
                assertThat(foundConsumed).isTrue();
                assertThat(foundProduced).isTrue();

            } else if ("stat".equals(type) && "DLQ message count".equals(title)) {
                foundDlqPanel = true;
                String expr = panel.get("targets").get(0).get("expr").asText();
                assertThat(expr).isEqualTo("sum(kafka_consumer_records_consumed_total{topic=\"trade-events-dlq\"})");
            }
        }

        assertThat(foundKafkaRow).as("Kafka row panel").isTrue();
        assertThat(foundConsumerLagPanel).as("Consumer lag panel").isTrue();
        assertThat(foundThroughputPanel).as("Throughput panel").isTrue();
        assertThat(foundDlqPanel).as("DLQ count panel").isTrue();
    }
}
