package com.northcart;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base class for all integration tests.
 *
 * <p>Every subclass shares ONE Spring context and ONE PostgreSQL container, because the
 * configuration is identical (Spring caches contexts by configuration). Different
 * annotations per test class would start a new context and container each time.</p>
 *
 * <p>Tests are not rolled back automatically (requests go through real transactions), so
 * each test uses unique emails via {@link #uniqueEmail()} to stay independent.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public abstract class AbstractIntegrationTest {

    protected static final String DEFAULT_PASSWORD = "Secret123";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected JdbcTemplate jdbc;

    /** No real SMTP server in tests; we only verify that sending was attempted. */
    @MockitoBean
    protected JavaMailSender mailSender;

    @BeforeEach
    void stubMailSender() {
        when(mailSender.createMimeMessage()).thenAnswer(inv -> new MimeMessage((Session) null));
    }

    protected static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@test.com";
    }

    protected String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    /** Registers a customer and returns its access token. */
    protected String registerCustomer(String email) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fullName", "Test User", "email", email, "password", DEFAULT_PASSWORD))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.accessToken");
    }

    protected String login(String email, String password) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.accessToken");
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }

    // ------------------------------------------------------------------ catalog test data

    private String cachedAdminToken;

    /** The bootstrap admin from application-test.yml; logged in once per test. */
    protected String adminToken() throws Exception {
        if (cachedAdminToken == null) {
            cachedAdminToken = login("admin@northcart.test", "Admin12345");
        }
        return cachedAdminToken;
    }

    protected static String unique() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    protected static long id(String json) {
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    /** Creates a fresh category through the admin API. Returns {id, slug}. */
    protected String[] createCategory() throws Exception {
        String body = mockMvc.perform(post("/api/v1/admin/categories")
                        .header("Authorization", bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Category " + unique()))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return new String[]{String.valueOf(id(body)), JsonPath.read(body, "$.slug")};
    }

    protected Map<String, Object> productBody(String categoryId, String name, String price, int stock) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("sku", "T-" + unique());
        body.put("description", name + " description");
        body.put("price", price);
        body.put("categoryId", Long.valueOf(categoryId));
        body.put("initialStock", stock);
        body.put("images", List.of(
                Map.of("url", "https://img.example.com/" + unique() + "-0.jpg", "altText", "front"),
                Map.of("url", "https://img.example.com/" + unique() + "-1.jpg", "altText", "back")));
        return body;
    }

    /** Creates a product through the admin API and returns the JSON response. */
    protected String createProduct(Map<String, Object> body) throws Exception {
        return mockMvc.perform(post("/api/v1/admin/products")
                        .header("Authorization", bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    protected String createProduct(String categoryId, String name, String price, int stock) throws Exception {
        return createProduct(productBody(categoryId, name, price, stock));
    }

    // ------------------------------------------------------------------ cart & order test data

    /** Saves an address for the customer and returns its id. */
    protected long createAddress(String token) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("fullName", "Khushi Kumar");
        body.put("phone", "+91 98765 43210");
        body.put("line1", "Flat 12, Green Residency");
        body.put("city", "Noida");
        body.put("state", "Uttar Pradesh");
        body.put("postalCode", "201301");
        String json = mockMvc.perform(post("/api/v1/users/me/addresses")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return id(json);
    }

    protected void addToCart(String token, long productId, int quantity) throws Exception {
        mockMvc.perform(post("/api/v1/cart/items")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("productId", productId, "quantity", quantity))))
                .andExpect(status().isOk());
    }

    /** Cart -> checkout in one step. Returns the order number. */
    protected String placeOrderFor(String token, long addressId, long productId, int quantity) throws Exception {
        addToCart(token, productId, quantity);
        String body = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("addressId", addressId))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.orderNumber");
    }

    /** Polls an async outcome (emails, refunds) for up to 5 seconds. */
    protected static void awaitTrue(String description, Callable<Boolean> condition) throws Exception {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            if (Boolean.TRUE.equals(condition.call())) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Timed out waiting for: " + description);
    }
}
