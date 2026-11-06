package com.northcart.common.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.SQLException;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fast unit test of the error contract: no Spring context, no database.
 * A tiny fake controller throws each kind of exception; we assert the JSON that comes out.
 */
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // Same strictness as application.yml (fail-on-unknown-properties: true)
        ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json()
                .failOnUnknownProperties(true)
                .build();

        mockMvc = MockMvcBuilders.standaloneSetup(new FakeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void invalidBody_returns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/test/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"\", \"quantity\": 0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.path").value("/test/items"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(2))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'quantity')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'name')]").exists());
    }

    @Test
    void unknownField_isRejected_notSilentlyIgnored() throws Exception {
        // Mass-assignment attempt: the client tries to sneak in a field the DTO doesn't have.
        mockMvc.perform(post("/test/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Mouse\", \"quantity\": 1, \"role\": \"ADMIN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.message").value("Unknown field 'role'"));
    }

    @Test
    void malformedJson_returns400() throws Exception {
        mockMvc.perform(post("/test/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed JSON request body"));
    }

    @Test
    void wrongPathVariableType_returns400() throws Exception {
        mockMvc.perform(get("/test/items/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void resourceNotFound_returns404() throws Exception {
        mockMvc.perform(get("/test/items/42"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Product not found: 42"));
    }

    @Test
    void insufficientStock_returns409() throws Exception {
        mockMvc.perform(get("/test/stock"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"))
                .andExpect(jsonPath("$.message").value("Only 2 unit(s) of 'Wireless Mouse' are available"));
    }

    @Test
    void uniqueConstraintViolation_isTranslatedToFriendlyMessage() throws Exception {
        mockMvc.perform(get("/test/duplicate-email"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"))
                .andExpect(jsonPath("$.message").value("An account with this email already exists"));
    }

    @Test
    void unexpectedException_returns500_withoutLeakingDetails() throws Exception {
        mockMvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(content().string(not(containsString("secret"))));
    }

    // ------------------------------------------------------------------
    // Test-only fixtures. Nested classes of test classes are excluded from
    // Spring Boot's component scan, so this never leaks into the real app.
    // ------------------------------------------------------------------

    record SampleRequest(@NotBlank String name, @NotNull @Min(1) Integer quantity) {
    }

    @RestController
    @RequestMapping("/test")
    static class FakeController {

        @PostMapping("/items")
        String create(@Valid @RequestBody SampleRequest request) {
            return "ok";
        }

        @GetMapping("/items/{id}")
        String get(@PathVariable Long id) {
            throw new ResourceNotFoundException("Product", id);
        }

        @GetMapping("/stock")
        String stock() {
            throw new InsufficientStockException("Wireless Mouse", 2);
        }

        @GetMapping("/duplicate-email")
        String duplicateEmail() {
            // What Spring Data throws when PostgreSQL rejects an INSERT on uk_users_email
            throw new DataIntegrityViolationException("could not execute statement",
                    new org.hibernate.exception.ConstraintViolationException(
                            "duplicate key", new SQLException("duplicate key", "23505"), "uk_users_email"));
        }

        @GetMapping("/boom")
        String boom() {
            throw new IllegalStateException("secret internal detail");
        }
    }
}
