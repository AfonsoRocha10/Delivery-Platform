package io.github.afonsorocha10.delivery.delivery;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

@Service
public class DeliveryService {

    private final DeliveryRepository repository;

    public DeliveryService(DeliveryRepository repository) {
        this.repository = repository;
    }

    public Delivery create(String recipientName, String address) {
        return repository.save(Delivery.create(recipientName, address));
    }

    public List<Delivery> findAll() {
        return repository.findAll();
    }

    public Delivery findById(UUID id) {
        return repository.findById(id).orElseThrow(() -> new DeliveryNotFoundException(id));
    }

    public Delivery updateStatus(UUID id, DeliveryStatus target) {
        return repository.update(id, current -> current.transitionTo(target))
                .orElseThrow(() -> new DeliveryNotFoundException(id));
    }
}