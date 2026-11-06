package com.northcart.auth;

import com.jayway.jsonpath.JsonPath;
import com.northcart.AbstractIntegrationTest;
import com.northcart.auth.event.PasswordResetRequestedEvent;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end: HTTP -> security filters -> controller -> service -> PostgreSQL. */
@RecordApplicationEvents
class AuthIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private ApplicationEvents events;

    // ---------------------------------------------------------------- register

    @Test
    void register_createsCustomerAndReturnsToken() throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fullName", "Khushi", "email", email.toUpperCase(),
                                "password", DEFAULT_PASSWORD))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.email").value(email))          // stored lower-case
                .andExpect(jsonPath("$.user.roles[0]").value("CUSTOMER"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist());

        String hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE email = ?", String.class, email);
        assertThat(hash).startsWith("$2a$12$").doesNotContain(DEFAULT_PASSWORD);
    }

    @Test
    void register_duplicateEmail_returns409() throws Exception {
        String email = uniqueEmail();
        registerCustomer(email);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fullName", "Again", "email", email, "password", DEFAULT_PASSWORD))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"));
    }

    @Test
    void register_weakPasswordAndBadEmail_returns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fullName", "X", "email", "not-an-email", "password", "short"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'email')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'password')]").exists());
    }

    @Test
    void register_cannotAssignOwnRole() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fullName", "Hacker", "email", uniqueEmail(),
                                "password", DEFAULT_PASSWORD, "roles", List.of("ADMIN")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unknown field 'roles'"));
    }

    // ---------------------------------------------------------------- login

    @Test
    void login_withCorrectCredentials_returnsToken() throws Exception {
        String email = uniqueEmail();
        registerCustomer(email);
        assertThat(login(email, DEFAULT_PASSWORD)).isNotBlank();
    }

    @Test
    void login_wrongPasswordAndUnknownEmail_lookIdentical() throws Exception {
        String email = uniqueEmail();
        registerCustomer(email);

        for (Map<String, String> body : List.of(
                Map.of("email", email, "password", "WrongPass1"),
                Map.of("email", uniqueEmail(), "password", "WrongPass1"))) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(body)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                    .andExpect(jsonPath("$.message").value("Invalid email or password"));
        }
    }

    @Test
    void disabledUser_cannotLogin_andExistingTokenStopsWorking() throws Exception {
        String email = uniqueEmail();
        String token = registerCustomer(email);
        jdbc.update("UPDATE users SET enabled = false WHERE email = ?", email);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", DEFAULT_PASSWORD))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", bearer(token)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("This account has been disabled"));
    }

    // ---------------------------------------------------------------- token handling

    @Test
    void protectedEndpoint_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Authentication is required"));
    }

    @Test
    void everyResponseCarriesARequestId_thatMatchesTheErrorBody() throws Exception {
        var result = mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("X-Request-Id"))
                .andReturn();
        String id = result.getResponse().getHeader("X-Request-Id");
        assertThat(JsonPath.<String>read(result.getResponse().getContentAsString(), "$.requestId")).isEqualTo(id);

        // A well-formed id sent by the client (e.g. the frontend or a gateway) is reused
        mockMvc.perform(get("/api/v1/users/me").header("X-Request-Id", "trace-12345678"))
                .andExpect(header().string("X-Request-Id", "trace-12345678"));
        // ...a malformed one is replaced, so it can't inject junk into our logs
        mockMvc.perform(get("/api/v1/users/me").header("X-Request-Id", "bad id\nINFO fake log line"))
                .andExpect(header().string("X-Request-Id", org.hamcrest.Matchers.matchesPattern("^[0-9a-f]{8}$")));
    }

    @Test
    void protectedEndpoint_withGarbageToken_returns401InvalidToken() throws Exception {
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid token"));
    }

    @Test
    void publicEndpoint_withGarbageToken_isStillReachable() throws Exception {
        // A bad token must not break public pages; the filter just doesn't authenticate.
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .header("Authorization", "Bearer not.a.jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", uniqueEmail()))))
                .andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- roles

    @Test
    void customer_cannotAccessAdminEndpoints() throws Exception {
        String token = registerCustomer(uniqueEmail());
        mockMvc.perform(get("/api/v1/admin/dashboard").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void admin_passesAdminRule() throws Exception {
        String token = login("admin@northcart.test", "Admin12345");
        // A non-existent admin URL: 404 (not 403) proves security LET THE REQUEST THROUGH.
        mockMvc.perform(get("/api/v1/admin/does-not-exist").header("Authorization", bearer(token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ENDPOINT_NOT_FOUND"));
    }

    @Test
    void admin_cannotUseCustomerOnlyEndpoints() throws Exception {
        String token = login("admin@northcart.test", "Admin12345");
        mockMvc.perform(get("/api/v1/cart").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- password reset

    @Test
    void forgotPassword_unknownEmail_returnsSameMessage_andSendsNothing() throws Exception {
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", uniqueEmail()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(
                        "If an account exists for this email, a password reset link has been sent."));

        assertThat(events.stream(PasswordResetRequestedEvent.class)).isEmpty();
    }

    @Test
    void fullPasswordResetFlow() throws Exception {
        String email = uniqueEmail();
        registerCustomer(email);

        // 1. Request a reset link
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email))))
                .andExpect(status().isOk());

        PasswordResetRequestedEvent event = events.stream(PasswordResetRequestedEvent.class)
                .filter(e -> e.email().equals(email))
                .findFirst().orElseThrow();
        assertThat(event.resetLink()).startsWith("http://localhost:5173/reset-password?token=");
        String token = event.resetLink().substring(event.resetLink().indexOf("token=") + 6);

        // Email is sent asynchronously after commit
        verify(mailSender, timeout(5000).atLeastOnce()).send(any(MimeMessage.class));

        // Only the hash is stored, never the raw token
        Integer rawStored = jdbc.queryForObject(
                "SELECT count(*) FROM password_reset_tokens WHERE token_hash = ?", Integer.class, token);
        assertThat(rawStored).isZero();

        // 2. Reset the password
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", token, "newPassword", "BrandNew456"))))
                .andExpect(status().isOk());

        // 3. New password works, old one doesn't
        assertThat(login(email, "BrandNew456")).isNotBlank();
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", DEFAULT_PASSWORD))))
                .andExpect(status().isUnauthorized());

        // 4. The link is single-use
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", token, "newPassword", "Another789"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void requestingANewLink_invalidatesThePreviousOne() throws Exception {
        String email = uniqueEmail();
        registerCustomer(email);

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/auth/forgot-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("email", email))))
                    .andExpect(status().isOk());
        }
        List<PasswordResetRequestedEvent> sent = events.stream(PasswordResetRequestedEvent.class)
                .filter(e -> e.email().equals(email)).toList();
        assertThat(sent).hasSize(2);
        String firstToken = sent.get(0).resetLink().substring(sent.get(0).resetLink().indexOf("token=") + 6);

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", firstToken, "newPassword", "BrandNew456"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }
}
