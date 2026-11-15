package io.github.afonsorocha10.delivery.delivery;

import java.util.UUID;

public class InvalidStatusTransitionException extends RuntimeException {

    public InvalidStatusTransitionException(UUID id, DeliveryStatus from, DeliveryStatus to) {
        super("Delivery " + id + " cannot move from " + from + " to " + to);
    }
}