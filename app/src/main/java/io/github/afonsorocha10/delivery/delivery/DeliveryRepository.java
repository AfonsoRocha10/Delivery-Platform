package io.github.afonsorocha10.delivery.delivery;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

import org.springframework.stereotype.Repository;

/**
 * In-memory storage. Data is lost on restart; this is a documented limitation of the first version.
 */
@Repository
public class DeliveryRepository {

    private final Map<UUID, Delivery> deliveries = new ConcurrentHashMap<>();

    public Delivery save(Delivery delivery) {
        deliveries.put(delivery.id(), delivery);
        return delivery;
    }

    public Optional<Delivery> findById(UUID id) {
        return Optional.ofNullable(deliveries.get(id));
    }

    public List<Delivery> findAll() {
        return List.copyOf(deliveries.values());
    }

    /**
     * Atomically replaces a delivery, so two concurrent status changes cannot both pass validation.
     */
    public Optional<Delivery> update(UUID id, UnaryOperator<Delivery> change) {
        return Optional.ofNullable(deliveries.computeIfPresent(id, (key, current) -> change.apply(current)));
    }
}