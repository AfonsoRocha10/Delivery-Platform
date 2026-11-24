package io.github.afonsorocha10.delivery.delivery;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Deliveries stored in PostgreSQL. Spring Data JPA generates the implementation at startup;
 * save, findById and findAll are inherited from {@link JpaRepository}.
 * Concurrent updates are guarded by the entity's {@code @Version} column, not by this repository.
 */
public interface DeliveryRepository extends JpaRepository<Delivery, UUID> {

    /**
     * All deliveries, oldest first, so listings are stable between calls.
     * The query is derived from the method name and checked against the entity at startup.
     */
    List<Delivery> findAllByOrderByCreatedAtAsc();
}
