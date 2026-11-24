package io.github.afonsorocha10.delivery.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import io.github.afonsorocha10.delivery.TestContainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.convention.TestBean;

/**
 * Starts the application with the {@code demo} profile, so the loader runs once at startup exactly as it
 * would for a real demo. The tests only read the data, except the re-run check, which must not change it.
 */
@SpringBootTest
@ActiveProfiles("demo")
@Import(TestContainersConfiguration.class)
class DemoDataLoaderTest {

    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");

    /** A fixed "now", so the expected time window is known. */
    @TestBean
    private Clock clock;

    @Autowired
    private DeliveryRepository repository;

    @Autowired
    private DemoDataLoader loader;

    static Clock clock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }

    @Test
    void loadsTheConfiguredNumberOfDeliveries() {
        assertThat(repository.count()).isEqualTo(DemoDataLoader.DELIVERY_COUNT);
    }

    @Test
    void coversEveryStatus() {
        List<DeliveryStatus> statuses = repository.findAll().stream().map(Delivery::status).distinct().toList();

        assertThat(statuses).containsExactlyInAnyOrder(DeliveryStatus.values());
    }

    @Test
    void timestampsLieInThePeriodBeforeNow() {
        Instant periodStart = NOW.minus(DemoDataLoader.PERIOD);

        assertThat(repository.findAll()).allSatisfy(delivery -> {
            assertThat(delivery.createdAt()).isBetween(periodStart, NOW);
            assertThat(delivery.updatedAt()).isBetween(delivery.createdAt(), NOW);
        });
    }

    @Test
    void runningAgainAddsNothing() {
        loader.run(new DefaultApplicationArguments());

        assertThat(repository.count()).isEqualTo(DemoDataLoader.DELIVERY_COUNT);
    }
}
