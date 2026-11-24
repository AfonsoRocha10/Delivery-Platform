package io.github.afonsorocha10.delivery.delivery;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Delivery use cases. Read-only transactions by default; methods that write override it.
 */
@Service
@Transactional(readOnly = true)
public class DeliveryService {

    private final DeliveryRepository repository;
    private final Clock clock;

    public DeliveryService(DeliveryRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public Delivery create(String recipientName, String address) {
        return repository.save(Delivery.create(recipientName, address, clock.instant()));
    }

    public List<Delivery> findAll() {
        return repository.findAllByOrderByCreatedAtAsc();
    }

    public Delivery findById(UUID id) {
        return repository.findById(id).orElseThrow(() -> new DeliveryNotFoundException(id));
    }

    /**
     * Changes the status without calling save: Hibernate writes the change at commit (dirty checking).
     * The UPDATE includes the version it read, so if another request changed the delivery in between,
     * the commit fails with an {@link org.springframework.orm.ObjectOptimisticLockingFailureException}
     * instead of overwriting that change.
     */
    @Transactional
    public Delivery updateStatus(UUID id, DeliveryStatus target) {
        Delivery delivery = findById(id);
        delivery.transitionTo(target, clock.instant());
        return delivery;
    }
}
