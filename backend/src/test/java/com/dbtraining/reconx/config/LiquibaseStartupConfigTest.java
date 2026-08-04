package com.dbtraining.reconx.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TICKET-ADV151 — Liquibase migrations on container startup.
 *
 * <p>Acceptance criteria covered:
 * <ul>
 *   <li>the profile compose runs with applies the changesets on boot</li>
 *   <li>{@code hibernate.ddl-auto} is {@code validate} — never {@code update}
 *       or {@code create} — because Liquibase owns the schema</li>
 *   <li>the backend does not race Postgres: {@code depends_on} gates on
 *       {@code service_healthy}</li>
 * </ul>
 */
class LiquibaseStartupConfigTest {

    /** The profile docker-compose activates for the backend container. */
    private static final String DOCKER_PROFILE = "uat";

    private static final YamlPropertySourceLoader LOADER = new YamlPropertySourceLoader();

    private String effectiveProperty(String key) {
        String profileValue = property("application-" + DOCKER_PROFILE + ".yml", key);
        return profileValue != null ? profileValue : property("application.yml", key);
    }

    private String property(String resource, String key) {
        try {
            List<PropertySource<?>> sources = LOADER.load(resource, new ClassPathResource(resource));
            for (PropertySource<?> source : sources) {
                Object value = source.getProperty(key);
                if (value != null) {
                    return String.valueOf(value);
                }
            }
            return null;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot load " + resource, e);
        }
    }

    @Test
    void dockerProfile_runsLiquibaseAgainstTheMasterChangelog() {
        assertThat(effectiveProperty("spring.liquibase.enabled")).isEqualTo("true");
        assertThat(effectiveProperty("spring.liquibase.change-log"))
                .isEqualTo("classpath:db/changelog/db.changelog-master.xml");
        assertThat(new ClassPathResource("db/changelog/db.changelog-master.xml").exists()).isTrue();
    }

    @Test
    void dockerProfile_leavesSchemaOwnershipToLiquibase() {
        assertThat(effectiveProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
    }

    @Test
    void dockerProfileFile_neverOverridesDdlAutoWithASchemaMutatingValue() {
        String profileValue = property("application-" + DOCKER_PROFILE + ".yml",
                "spring.jpa.hibernate.ddl-auto");

        assertThat(profileValue).isNotIn("update", "create", "create-drop");
    }

    @Test
    void backendWaitsForPostgresToBeHealthyBeforeMigrating() {
        Map<String, Object> dependsOn =
                DeploymentFiles.mapAt(ComposeFile.service("backend"), "depends_on");

        assertThat(DeploymentFiles.mapAt(dependsOn, "postgres"))
                .containsEntry("condition", "service_healthy");
    }

    @Test
    void backendContainerRunsTheDockerProfile() {
        Map<String, Object> environment =
                DeploymentFiles.mapAt(ComposeFile.service("backend"), "environment");

        assertThat(environment).containsEntry("SPRING_PROFILES_ACTIVE", DOCKER_PROFILE);
    }
}
