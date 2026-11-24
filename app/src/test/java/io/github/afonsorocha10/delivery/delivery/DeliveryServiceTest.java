package io.github.afonsorocha10.delivery.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import io.github.afonsorocha10.delivery.TestContainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs the real service against a real PostgreSQL (Testcontainers), with no mocks.
 * Deliberately not {@code @Transactional}: each service call commits, as in production, so the tests
 * see what was actually written to the database, not Hibernate's in-memory copy.
 * The application's {@link Clock} is replaced by a {@link MutableClock}, so timestamps are known in advance.
 */
@SpringBootTest
@Import(TestContainersConfiguration.class)
class DeliveryServiceTest {

    private static final Instant T0 = Instant.parse("2026-01-15T10:00:00Z");

    /** Static because {@link #clock()} must be static; the tests move it, the service reads it. */
    private static final MutableClock CLOCK = new MutableClock(T0);

    /** Replaces the application's Clock bean with the one returned by {@link #clock()}. */
    @TestBean
    private Clock clock;

    @Autowired
    private DeliveryService service;

    @Autowired
    private DeliveryRepository repository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    static Clock clock() {
        return CLOCK;
    }

    @BeforeEach
    void setUp() {
        // Committed rows stay in the shared database, so start each test from an empty table.
        repository.deleteAll();
        CLOCK.setInstant(T0);
    }

    @Test
    void createdDeliveryStartsAsCreatedAndCanBeFound() {
        Delivery created = service.create("Ana Silva", "Rua Augusta 1, Lisboa");

        assertThat(created.id()).isNotNull();
        assertThat(created.status()).isEqualTo(DeliveryStatus.CREATED);
        assertThat(service.findById(created.id())).isEqualTo(created);
        assertThat(service.findAll()).containsExactly(created);
    }

    @Test
    void createSetsBothTimestampsFromTheClock() {
        Delivery created = service.create("Ana Silva", "Rua Augusta 1, Lisboa");

        Delivery stored = service.findById(created.id());
        assertThat(stored.createdAt()).isEqualTo(T0);
        assertThat(stored.updatedAt()).isEqualTo(T0);
    }

    @Test
    void timestampsAreStoredWithMicrosecondPrecision() {
        // PostgreSQL keeps microseconds; the entity truncates so the returned object matches what is read back.
        CLOCK.setInstant(Instant.parse("2026-01-15T10:00:00.123456789Z"));

        Delivery created = service.create("Ana Silva", "Rua Augusta 1, Lisboa");

        Instant expected = Instant.parse("2026-01-15T10:00:00.123456Z");
        assertThat(created.createdAt()).isEqualTo(expected);
        assertThat(service.findById(created.id()).createdAt()).isEqualTo(expected);
    }

    @Test
    void validTransitionIsPersisted() {
        Delivery created = service.create("Ana Silva", "Rua Augusta 1, Lisboa");

        service.updateStatus(created.id(), DeliveryStatus.PICKED_UP);

        assertThat(service.findById(created.id()).status()).isEqualTo(DeliveryStatus.PICKED_UP);
    }

    @Test
    void transitionChangesUpdatedAtButNotCreatedAt() {
        Delivery created = service.create("Ana Silva", "Rua Augusta 1, Lisboa");
        CLOCK.advance(Duration.ofMinutes(5));

        service.updateStatus(created.id(), DeliveryStatus.PICKED_UP);

        Delivery stored = service.findById(created.id());
        assertThat(stored.createdAt()).isEqualTo(T0);
        assertThat(stored.updatedAt()).isEqualTo(T0.plus(Duration.ofMinutes(5)));
    }

    @Test
    void invalidTransitionIsRejectedAndDeliveryIsUnchanged() {
        Delivery created = service.create("Ana Silva", "Rua Augusta 1, Lisboa");
        CLOCK.advance(Duration.ofMinutes(5));

        assertThatThrownBy(() -> service.updateStatus(created.id(), DeliveryStatus.DELIVERED))
                .isInstanceOf(InvalidStatusTransitionException.class);

        Delivery stored = service.findById(created.id());
        assertThat(stored.status()).isEqualTo(DeliveryStatus.CREATED);
        assertThat(stored.updatedAt()).isEqualTo(T0);
    }

    @Test
    void findAllReturnsOldestFirst() {
        // Insert the newer delivery first, so the order can only come from created_at, not from insertion order.
        CLOCK.setInstant(T0.plus(Duration.ofHours(1)));
        Delivery newer = service.create("Bruno Costa", "Avenida da Liberdade 10, Lisboa");
        CLOCK.setInstant(T0);
        Delivery older = service.create("Ana Silva", "Rua Augusta 1, Lisboa");

        assertThat(service.findAll()).containsExactly(older, newer);
    }

    /**
     * Replays two overlapping requests on one thread, so the interleaving is always the same:
     * request A reads the delivery, request B changes it and commits, then request A commits its own change.
     * Both changes are valid from CREATED, so only the version check can stop A from overwriting B.
     */
    @Test
    void concurrentUpdateIsRejectedInsteadOfOverwritten() {
        UUID id = service.create("Ana Silva", "Rua Augusta 1, Lisboa").id();
        TransactionTemplate requestA = new TransactionTemplate(transactionManager);
        TransactionTemplate requestB = new TransactionTemplate(transactionManager);
        // REQUIRES_NEW: B runs in its own transaction and commits while A's transaction is still open.
        requestB.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        assertThatThrownBy(() -> requestA.executeWithoutResult(txA -> {
            Delivery staleCopy = repository.findById(id).orElseThrow();   // A reads version 0

            requestB.executeWithoutResult(txB ->
                    service.updateStatus(id, DeliveryStatus.PICKED_UP));  // B commits version 1

            staleCopy.transitionTo(DeliveryStatus.CANCELLED, CLOCK.instant()); // A commits on version 0
        })).isInstanceOf(OptimisticLockingFailureException.class);

        // B's change survives; without @Version the delivery would now be CANCELLED after being picked up.
        assertThat(service.findById(id).status()).isEqualTo(DeliveryStatus.PICKED_UP);
    }

    @Test
    void unknownIdIsReportedAsNotFound() {
        UUID unknown = UUID.randomUUID();

        assertThatThrownBy(() -> service.findById(unknown)).isInstanceOf(DeliveryNotFoundException.class);
        assertThatThrownBy(() -> service.updateStatus(unknown, DeliveryStatus.PICKED_UP))
                .isInstanceOf(DeliveryNotFoundException.class);
    }

    /** A clock that only moves when a test moves it. */
    static final class MutableClock extends Clock {

        private volatile Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void setInstant(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            this.instant = instant.plus(duration);
        }

        @Override
        public Instant instant() {
            return instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant, zone);
        }
    }
}
