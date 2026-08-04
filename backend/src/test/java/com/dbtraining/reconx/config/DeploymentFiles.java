package com.dbtraining.reconx.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * TICKET-ADV149 / ADV150 / ADV151 / ADV152 — shared loader for the deployment
 * artifacts that live outside the Maven module (docker-compose.yml, the
 * Prometheus scrape config, the Grafana provisioning tree).
 *
 * <p>The repository root is discovered by walking up from the working directory
 * until a {@code docker-compose.yml} is found, so the tests run identically from
 * the module directory and from the reactor root.
 */
final class DeploymentFiles {

    private static final Yaml YAML = new Yaml();
    private static final ObjectMapper JSON = new ObjectMapper();

    private DeploymentFiles() {
    }

    static Path repoRoot() {
        Path candidate = Paths.get("").toAbsolutePath();
        while (candidate != null) {
            if (Files.isRegularFile(candidate.resolve("docker-compose.yml"))) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException("docker-compose.yml not found in any parent of "
                + Paths.get("").toAbsolutePath());
    }

    static Map<String, Object> loadYaml(String relativePath) {
        Path path = repoRoot().resolve(relativePath);
        try {
            return YAML.load(Files.newInputStream(path));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + path, e);
        }
    }

    static JsonNode loadJson(String relativePath) {
        Path path = repoRoot().resolve(relativePath);
        try {
            return JSON.readTree(path.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + path, e);
        }
    }

    static String readText(String relativePath) {
        Path path = repoRoot().resolve(relativePath);
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + path, e);
        }
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> mapAt(Map<String, Object> root, String... keys) {
        Map<String, Object> current = root;
        for (String key : keys) {
            Object next = current.get(key);
            if (!(next instanceof Map)) {
                return Map.of();
            }
            current = (Map<String, Object>) next;
        }
        return current;
    }

    @SuppressWarnings("unchecked")
    static List<Object> listAt(Map<String, Object> root, String key) {
        Object value = root.get(key);
        return value instanceof List ? (List<Object>) value : List.of();
    }
}
