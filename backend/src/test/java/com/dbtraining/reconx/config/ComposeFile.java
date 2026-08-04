package com.dbtraining.reconx.config;

import java.util.Map;
import java.util.Set;

/**
 * TICKET-ADV152 — typed access to the docker-compose service definitions the
 * deployment tests assert against.
 */
final class ComposeFile {

    /** Every service that must be up and healthy for the demo stack (ADV148/ADV152). */
    static final Set<String> ALL_SERVICES = Set.of(
            "postgres", "zookeeper", "kafka", "backend", "frontend", "prometheus", "grafana", "kafdrop");

    private ComposeFile() {
    }

    static Map<String, Object> services() {
        return DeploymentFiles.mapAt(DeploymentFiles.loadYaml("docker-compose.yml"), "services");
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> service(String name) {
        Object service = services().get(name);
        if (!(service instanceof Map)) {
            throw new AssertionError("No service named '" + name + "' in docker-compose.yml");
        }
        return (Map<String, Object>) service;
    }
}
