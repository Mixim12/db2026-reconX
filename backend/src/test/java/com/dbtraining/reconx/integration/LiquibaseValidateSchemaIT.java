package com.dbtraining.reconx.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ============================================================================
 * TICKET-ADV151 — Liquibase migrations on container startup
 *
 * WHAT:    Boots the application against a fresh PostgreSQL container with
 *          `ddl-auto: validate` — the value the docker (uat) profile pins —
 *          instead of the `update` the plain test profile uses.
 * WHY:     `validate` only survives boot if Liquibase created *every* table
 *          Hibernate maps, including the Envers revinfo/trades_aud pair. A
 *          missing changeset shows up here as a failed context load, which is
 *          exactly how the backend container would fail to reach healthy on a
 *          `docker compose down -v && docker compose up -d`.
 * ============================================================================
 */
@Testcontainers
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.liquibase.enabled=true",
        "spring.liquibase.change-log=classpath:db/changelog/db.changelog-master.xml"
})
@ActiveProfiles("test")
class LiquibaseValidateSchemaIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    Environment environment;

    @Test
    void applicationBootsAgainstAFreshDatabaseWithHibernateInValidateMode() {
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");

        Integer applied = jdbc.queryForObject("SELECT COUNT(*) FROM databasechangelog", Integer.class);
        assertThat(applied).isGreaterThanOrEqualTo(13);
    }

    @Test
    void liquibaseOwnsTheEnversTablesHibernateWouldOtherwiseCreate() {
        assertThat(tableExists("revinfo")).isTrue();
        assertThat(tableExists("trades_aud")).isTrue();
    }

    private boolean tableExists(String table) {
        Boolean exists = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM information_schema.tables "
                        + "WHERE table_schema = current_schema() AND table_name = ?)",
                Boolean.class, table);
        return Boolean.TRUE.equals(exists);
    }
}
