package com.jobfit.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared Testcontainers Postgres setup for integration tests. Spring Boot's
 * @ServiceConnection auto-wires the datasource properties from the running
 * container - no manual JDBC URL wiring needed. Requires a Docker daemon,
 * which is what CI provides (see .github/workflows/ci.yml); this is why
 * these tests are separated from the pure-Mockito unit tests.
 */
@TestConfiguration
public class PostgresTestContainerConfig {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));
    }
}
