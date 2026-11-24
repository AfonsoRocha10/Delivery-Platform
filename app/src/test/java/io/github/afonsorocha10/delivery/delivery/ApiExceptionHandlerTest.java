package io.github.afonsorocha10.delivery.delivery;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Checks exception-to-HTTP mappings that are hard to trigger through the real API.
 * Standalone MockMvc: no Spring Boot context and no database, only the handler and a stub controller,
 * so it also proves Spring routes the exception to the handler, not just that the handler method works.
 */
class ApiExceptionHandlerTest {

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ConflictingController())
            .setControllerAdvice(new ApiExceptionHandler())
            .build();

    @Test
    void concurrentUpdateReturns409ProblemWithoutInternalDetails() throws Exception {
        mvc.perform(get("/concurrent-update"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("The delivery was changed by another request; reload it and try again"))
                // The exception message names the entity class; clients must not see internal names.
                .andExpect(jsonPath("$.detail", not(containsString(Delivery.class.getName()))));
    }

    /** Throws the same exception type that a failed version check produces at commit. */
    @RestController
    static class ConflictingController {

        @GetMapping("/concurrent-update")
        void update() {
            throw new ObjectOptimisticLockingFailureException(Delivery.class, UUID.randomUUID());
        }
    }
}
