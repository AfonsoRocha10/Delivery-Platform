package io.github.afonsorocha10.delivery.delivery;

/**
 * Lifecycle of a delivery.
 *
 * <pre>
 * CREATED -> PICKED_UP -> IN_TRANSIT -> DELIVERED
 *    |
 *    +-> CANCELLED (only before pickup)
 * </pre>
 */
public enum DeliveryStatus {
    CREATED,
    PICKED_UP,
    IN_TRANSIT,
    DELIVERED,
    CANCELLED;

    public boolean canTransitionTo(DeliveryStatus target) {
        return switch (this) {
            case CREATED -> target == PICKED_UP || target == CANCELLED;
            case PICKED_UP -> target == IN_TRANSIT;
            case IN_TRANSIT -> target == DELIVERED;
            case DELIVERED, CANCELLED -> false;
        };
    }
}