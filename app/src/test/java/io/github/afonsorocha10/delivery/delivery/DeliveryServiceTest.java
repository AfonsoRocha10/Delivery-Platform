package io.github.afonsorocha10.delivery.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Uses the real in-memory repository: it is trivial, so mocking it would only test the mock.
 */
class DeliveryServiceTest {

    private DeliveryService service;

    @BeforeEach
    void setUp() {
        service = new DeliveryService(new DeliveryRepository());
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
    void validTransitionIsPersisted() {
        Delivery created = service.create("Ana Silva", "Rua Augusta 1, Lisboa");

        service.updateStatus(created.id(), DeliveryStatus.PICKED_UP);

        assertThat(service.findById(created.id()).status()).isEqualTo(DeliveryStatus.PICKED_UP);
    }

    @Test
    void invalidTransitionIsRejectedAndStatusIsUnchanged() {
        Delivery created = service.create("Ana Silva", "Rua Augusta 1, Lisboa");

        assertThatThrownBy(() -> service.updateStatus(created.id(), DeliveryStatus.DELIVERED))
                .isInstanceOf(InvalidStatusTransitionException.class);
        assertThat(service.findById(created.id()).status()).isEqualTo(DeliveryStatus.CREATED);
    }

    @Test
    void unknownIdIsReportedAsNotFound() {
        UUID unknown = UUID.randomUUID();

        assertThatThrownBy(() -> service.findById(unknown)).isInstanceOf(DeliveryNotFoundException.class);
        assertThatThrownBy(() -> service.updateStatus(unknown, DeliveryStatus.PICKED_UP))
                .isInstanceOf(DeliveryNotFoundException.class);
    }
}