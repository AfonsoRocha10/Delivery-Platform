package io.github.afonsorocha10.delivery.delivery;

import java.util.UUID;

public class DeliveryNotFoundException extends RuntimeException {

    public DeliveryNotFoundException(UUID id) {
        super("Delivery " + id + " was not found");
    }
}