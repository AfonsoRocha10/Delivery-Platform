package io.github.afonsorocha10.delivery;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Throwaway PostgreSQL for tests. {@link ServiceConnection} gives Spring the container's URL and credentials,
 * so tests need no datasource settings. Import it with {@code @Import(TestContainersConfiguration.class)}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestContainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:18.7-alpine3.24");
    }
}
