package io.github.afonsorocha10.delivery;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Starts the full application on a random port and checks it over real HTTP, the same way
 * CI, Docker health checks and deployment scripts will reach it.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class DeliveryServiceApplicationTests {

    @Autowired
    private RestTestClient client;

    @ParameterizedTest
    @ValueSource(strings = {"/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness"})
    void healthEndpointsReportUp(String path) {
        client.get().uri(path)
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    void otherActuatorEndpointsAreNotExposed() {
        client.get().uri("/actuator/info")
                .exchange()
                .expectStatus().isNotFound();
    }
}