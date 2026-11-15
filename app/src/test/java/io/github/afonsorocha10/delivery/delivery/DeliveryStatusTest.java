package io.github.afonsorocha10.delivery.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class DeliveryStatusTest {

    @ParameterizedTest(name = "{0} -> {1} is allowed")
    @CsvSource({
            "CREATED, PICKED_UP",
            "CREATED, CANCELLED",
            "PICKED_UP, IN_TRANSIT",
            "IN_TRANSIT, DELIVERED"
    })
    void allowsForwardTransitions(DeliveryStatus from, DeliveryStatus to) {
        assertThat(from.canTransitionTo(to)).isTrue();
    }

    @ParameterizedTest(name = "{0} -> {1} is rejected")
    @CsvSource({
            "CREATED, CREATED",        // no-op transition
            "CREATED, IN_TRANSIT",     // skipping a step
            "CREATED, DELIVERED",      // skipping several steps
            "PICKED_UP, CREATED",      // going backwards
            "PICKED_UP, CANCELLED",    // cancelling after pickup
            "IN_TRANSIT, PICKED_UP",   // going backwards
            "IN_TRANSIT, CANCELLED"    // cancelling in transit
    })
    void rejectsInvalidTransitions(DeliveryStatus from, DeliveryStatus to) {
        assertThat(from.canTransitionTo(to)).isFalse();
    }

    @ParameterizedTest(name = "DELIVERED -> {0} is rejected")
    @EnumSource(DeliveryStatus.class)
    void deliveredIsFinal(DeliveryStatus target) {
        assertThat(DeliveryStatus.DELIVERED.canTransitionTo(target)).isFalse();
    }

    @ParameterizedTest(name = "CANCELLED -> {0} is rejected")
    @EnumSource(DeliveryStatus.class)
    void cancelledIsFinal(DeliveryStatus target) {
        assertThat(DeliveryStatus.CANCELLED.canTransitionTo(target)).isFalse();
    }
}