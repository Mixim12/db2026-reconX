package com.dbtraining.reconx.observability;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component("reconxKafka")
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
public class KafkaHealthIndicator extends AbstractHealthIndicator {

    private static final int REQUEST_TIMEOUT_MILLIS = 2_000;
    private static final int DEFAULT_API_TIMEOUT_MILLIS = 3_000;
    private static final int CLUSTER_LOOKUP_TIMEOUT_SECONDS = 2;

    private final String bootstrapServers;
    private final ClusterProbe clusterProbe;

    @Autowired
    public KafkaHealthIndicator(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        this(bootstrapServers, config -> {
            try (AdminClient adminClient = AdminClient.create(config)) {
                DescribeClusterResult cluster = adminClient.describeCluster();
                String clusterId = cluster.clusterId()
                        .get(CLUSTER_LOOKUP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                int nodeCount = cluster.nodes()
                        .get(CLUSTER_LOOKUP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        .size();
                return new ClusterDetails(clusterId, nodeCount);
            }
        });
    }

    KafkaHealthIndicator(String bootstrapServers, ClusterProbe clusterProbe) {
        super("ReconX Kafka health check failed");
        this.bootstrapServers = bootstrapServers;
        this.clusterProbe = clusterProbe;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        Map<String, Object> config = Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, REQUEST_TIMEOUT_MILLIS,
                AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, DEFAULT_API_TIMEOUT_MILLIS);

        try {
            ClusterDetails cluster = clusterProbe.probe(config);
            builder.up()
                    .withDetail("clusterId", cluster.clusterId())
                    .withDetail("nodeCount", cluster.nodeCount());
        } catch (Exception exception) {
            builder.down(exception);
        }
    }

    @FunctionalInterface
    interface ClusterProbe {
        ClusterDetails probe(Map<String, Object> config) throws Exception;
    }

    record ClusterDetails(String clusterId, int nodeCount) { }
}
