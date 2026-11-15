package io.github.afonsorocha10.delivery.delivery;

import java.util.UUID;

/**
 * Immutable delivery. Status changes produce a new instance, which keeps updates atomic in the repository.
 */
public record Delivery(UUID id, String recipientName, String address, DeliveryStatus status) {

    public static Delivery create(String recipientName, String address) {
        return new Delivery(UUID.randomUUID(), recipientName, address, DeliveryStatus.CREATED);
    }

    public Delivery transitionTo(DeliveryStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidStatusTransitionException(id, status, target);
        }
        return new Delivery(id, recipientName, address, target);
    }
}