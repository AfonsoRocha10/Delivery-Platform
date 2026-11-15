package io.github.afonsorocha10.delivery.delivery;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/deliveries")
public class DeliveryController {

    private final DeliveryService service;

    public DeliveryController(DeliveryService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<DeliveryResponse> create(@Valid @RequestBody CreateDeliveryRequest request) {
        Delivery delivery = service.create(request.recipientName(), request.address());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(delivery.id())
                .toUri();
        return ResponseEntity.created(location).body(DeliveryResponse.from(delivery));
    }

    @GetMapping
    public List<DeliveryResponse> findAll() {
        return service.findAll().stream().map(DeliveryResponse::from).toList();
    }

    @GetMapping("/{id}")
    public DeliveryResponse findById(@PathVariable UUID id) {
        return DeliveryResponse.from(service.findById(id));
    }

    @PatchMapping("/{id}/status")
    public DeliveryResponse updateStatus(@PathVariable UUID id, @Valid @RequestBody UpdateStatusRequest request) {
        return DeliveryResponse.from(service.updateStatus(id, request.status()));
    }

    public record CreateDeliveryRequest(
            @NotBlank @Size(max = 100) String recipientName,
            @NotBlank @Size(max = 255) String address) {
    }

    public record UpdateStatusRequest(@NotNull DeliveryStatus status) {
    }

    public record DeliveryResponse(UUID id, String recipientName, String address, DeliveryStatus status) {

        static DeliveryResponse from(Delivery delivery) {
            return new DeliveryResponse(delivery.id(), delivery.recipientName(), delivery.address(), delivery.status());
        }
    }
}