package com.dbtraining.reconx.service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Uses the same wiring as the other Testcontainers tests: @ServiceConnection
 * points the whole DataSource at the container, and the "test" profile keeps
 * the dev profile out of the way — dev pins driver-class-name to org.h2.Driver,
 * which Hikari then rejects for a jdbc:postgresql URL.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class ReconciliationIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("reconx_test")
            .withUsername("test")
            .withPassword("test");

    @Test
    void contextLoads() {
        assertThat(postgres.isRunning()).isTrue();
    }
}
