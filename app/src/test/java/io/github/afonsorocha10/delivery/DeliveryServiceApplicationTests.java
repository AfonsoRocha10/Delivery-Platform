package io.github.afonsorocha10.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.afonsorocha10.delivery.delivery.DeliveryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Starts the full application on a random port and checks it over real HTTP, the same way
 * CI, Docker health checks and deployment scripts will reach it.
 * Uses a real PostgreSQL (Testcontainers): readiness includes the database, so "UP" also proves it is reachable.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestContainersConfiguration.class)
class DeliveryServiceApplicationTests {

    @Autowired
    private RestTestClient client;

    @Autowired
    private DeliveryRepository repository;

    @ParameterizedTest
    @ValueSource(strings = {"/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness"})
    void healthEndpointsReportUp(String path) {
        client.get().uri(path)
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    void demoDataIsNotLoadedWithoutTheDemoProfile() {
        // Nothing in this test class creates deliveries, so any row here would come from the demo loader.
        assertThat(repository.count()).isZero();
    }

    @Test
    void otherActuatorEndpointsAreNotExposed() {
        client.get().uri("/actuator/info")
                .exchange()
                .expectStatus().isNotFound();
    }
}