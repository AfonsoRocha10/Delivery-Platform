package io.github.afonsorocha10.delivery.delivery;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import io.github.afonsorocha10.delivery.TestContainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Verifies the HTTP contract (status codes, headers, error bodies) through MockMvc.
 * Runs the full application against a real PostgreSQL (Testcontainers) instead of mocking the
 * service or the repository, so each request goes through the real transactions and database.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestContainersConfiguration.class)
class DeliveryControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private DeliveryService service;

    @Test
    void createReturns201WithLocationAndBody() throws Exception {
        mvc.perform(post("/api/deliveries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recipientName": "Ana Silva", "address": "Rua Augusta 1, Lisboa"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/deliveries/")))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());
    }

    @Test
    void createWithInvalidFieldsReturns400ProblemListingEachField() throws Exception {
        mvc.perform(post("/api/deliveries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recipientName": " "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                // Check the fields, not the message text: messages are localised and depend on the machine's locale.
                .andExpect(jsonPath("$.errors.recipientName").isNotEmpty())
                .andExpect(jsonPath("$.errors.address").isNotEmpty());
    }

    @Test
    void listContainsCreatedDelivery() throws Exception {
        // The database is shared between tests and rows from other tests stay in it,
        // so assert on this delivery rather than on the total count.
        Delivery delivery = service.create("Ana Silva", "Rua Augusta 1, Lisboa");

        mvc.perform(get("/api/deliveries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(delivery.id().toString())));
    }

    @Test
    void getUnknownIdReturns404Problem() throws Exception {
        UUID unknown = UUID.randomUUID();

        mvc.perform(get("/api/deliveries/{id}", unknown))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail", endsWith("was not found")));
    }

    @Test
    void validStatusChangeReturns200() throws Exception {
        Delivery delivery = service.create("Ana Silva", "Rua Augusta 1, Lisboa");

        mvc.perform(patch("/api/deliveries/{id}/status", delivery.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "PICKED_UP"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PICKED_UP"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());
    }

    @Test
    void invalidStatusChangeReturns409Problem() throws Exception {
        Delivery delivery = service.create("Ana Silva", "Rua Augusta 1, Lisboa");

        mvc.perform(patch("/api/deliveries/{id}/status", delivery.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "DELIVERED"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void unknownStatusValueReturns400() throws Exception {
        Delivery delivery = service.create("Ana Silva", "Rua Augusta 1, Lisboa");

        mvc.perform(patch("/api/deliveries/{id}/status", delivery.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "TELEPORTED"}
                                """))
                .andExpect(status().isBadRequest());
    }
}