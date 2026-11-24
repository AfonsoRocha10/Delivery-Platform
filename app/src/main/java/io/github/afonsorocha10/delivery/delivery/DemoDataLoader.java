package io.github.afonsorocha10.delivery.delivery;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Fills an empty database with realistic sample deliveries, only when the {@code demo} profile is active
 * ({@code SPRING_PROFILES_ACTIVE=demo}). Production, CI and the normal tests never load it.
 *
 * <ul>
 *   <li>Same data on every run: names, addresses, statuses and time offsets come from a fixed seed
 *       (only the ids differ).</li>
 *   <li>Runs only if the table is empty, so restarting with {@code demo} adds no duplicates.</li>
 *   <li>Every delivery goes through {@link Delivery#transitionTo}, so the data can only contain
 *       status histories the lifecycle allows.</li>
 *   <li>Dated in the {@link #PERIOD} before the {@link Clock}'s "now"; no step is later than now.</li>
 * </ul>
 *
 * Java instead of a Flyway migration: Flyway runs every migration in every environment, and migrations
 * describe the schema, not sample data (decision 7).
 */
@Component
@Profile("demo")
class DemoDataLoader implements ApplicationRunner {

    static final int DELIVERY_COUNT = 300;
    static final Duration PERIOD = Duration.ofDays(30);

    private static final long SEED = 42;
    private static final Duration MIN_STEP = Duration.ofMinutes(10);
    private static final Duration MAX_STEP = Duration.ofHours(6);

    private static final List<String> FIRST_NAMES = List.of(
            "Ana", "Beatriz", "Carolina", "Catarina", "Diana", "Inês", "Joana", "Leonor", "Mariana", "Marta",
            "Rita", "Sofia", "Afonso", "André", "Bruno", "Diogo", "Duarte", "Francisco", "Gonçalo", "João",
            "Miguel", "Nuno", "Pedro", "Rodrigo", "Tiago");
    private static final List<String> SURNAMES = List.of(
            "Silva", "Santos", "Ferreira", "Pereira", "Oliveira", "Costa", "Rodrigues", "Martins", "Jesus",
            "Sousa", "Fernandes", "Gonçalves", "Gomes", "Lopes", "Marques", "Alves", "Almeida", "Ribeiro",
            "Pinto", "Carvalho", "Teixeira", "Moreira", "Correia", "Mendes", "Nunes");
    private static final List<String> STREETS = List.of(
            "Rua Augusta", "Rua de Santa Catarina", "Avenida da Liberdade", "Rua do Comércio",
            "Avenida da República", "Rua da Prata", "Rua Direita", "Avenida dos Aliados", "Rua das Flores",
            "Rua 25 de Abril", "Rua do Sol", "Avenida Central", "Travessa do Carmo", "Largo do Rossio",
            "Rua de Cedofeita");
    private static final List<String> CITIES = List.of(
            "Lisboa", "Porto", "Braga", "Coimbra", "Faro", "Aveiro", "Setúbal", "Évora", "Viseu", "Leiria",
            "Guimarães", "Funchal");

    private static final Logger log = LoggerFactory.getLogger(DemoDataLoader.class);

    private final DeliveryRepository repository;
    private final Clock clock;

    DemoDataLoader(DeliveryRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (repository.count() > 0) {
            log.info("Demo data skipped: the delivery table is not empty");
            return;
        }
        Random random = new Random(SEED);
        Instant now = clock.instant();
        List<Delivery> deliveries = new ArrayList<>(DELIVERY_COUNT);
        for (int i = 0; i < DELIVERY_COUNT; i++) {
            deliveries.add(randomDelivery(random, now));
        }
        // One transaction for all rows: either the whole dataset is saved or none of it.
        repository.saveAll(deliveries);
        log.info("Loaded {} demo deliveries", deliveries.size());
    }

    private static Delivery randomDelivery(Random random, Instant now) {
        String name = pick(random, FIRST_NAMES) + " " + pick(random, SURNAMES);
        String address = pick(random, STREETS) + " " + (1 + random.nextInt(250)) + ", " + pick(random, CITIES);
        Instant time = now.minusSeconds(random.nextLong(PERIOD.toSeconds()));
        Delivery delivery = Delivery.create(name, address, time);

        for (DeliveryStatus step : stepsTo(randomTargetStatus(random))) {
            time = time.plusSeconds(random.nextLong(MIN_STEP.toSeconds(), MAX_STEP.toSeconds()));
            if (time.isAfter(now)) {
                break; // A recent delivery has not got this far yet.
            }
            delivery.transitionTo(step, time);
        }
        return delivery;
    }

    /** Roughly a day of deliveries: most done, some on the way, a few new or cancelled. */
    private static DeliveryStatus randomTargetStatus(Random random) {
        int percent = random.nextInt(100);
        if (percent < 10) {
            return DeliveryStatus.CREATED;
        } else if (percent < 25) {
            return DeliveryStatus.PICKED_UP;
        } else if (percent < 45) {
            return DeliveryStatus.IN_TRANSIT;
        } else if (percent < 90) {
            return DeliveryStatus.DELIVERED;
        }
        return DeliveryStatus.CANCELLED;
    }

    /** The transitions from CREATED to {@code target}, in order. */
    private static List<DeliveryStatus> stepsTo(DeliveryStatus target) {
        return switch (target) {
            case CREATED -> List.of();
            case PICKED_UP -> List.of(DeliveryStatus.PICKED_UP);
            case IN_TRANSIT -> List.of(DeliveryStatus.PICKED_UP, DeliveryStatus.IN_TRANSIT);
            case DELIVERED -> List.of(DeliveryStatus.PICKED_UP, DeliveryStatus.IN_TRANSIT, DeliveryStatus.DELIVERED);
            case CANCELLED -> List.of(DeliveryStatus.CANCELLED);
        };
    }

    private static String pick(Random random, List<String> values) {
        return values.get(random.nextInt(values.size()));
    }
}
