package com.northcart;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Starts a throwaway PostgreSQL 16 container for integration tests.
 *
 * <p>{@code @ServiceConnection} makes Spring Boot point spring.datasource.* at the
 * container automatically - no URLs or passwords in test config. Same major version as
 * docker-compose.yml, so tests see exactly the database behaviour production sees.</p>
 *
 * <p>Requires Docker to be running.</p>
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));
    }
}
