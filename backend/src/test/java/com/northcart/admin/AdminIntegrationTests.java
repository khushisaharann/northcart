package com.northcart.admin;

import com.jayway.jsonpath.JsonPath;
import com.northcart.AbstractIntegrationTest;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.mockingDetails;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminIntegrationTests extends AbstractIntegrationTest {

    private String customerEmail;
    private String customer;
    private long addressId;
    private long productId;

    @BeforeEach
    void setUp() throws Exception {
        customerEmail = uniqueEmail();
        customer = registerCustomer(customerEmail);
        addressId = createAddress(customer);
        productId = id(createProduct(createCategory()[0], "Admin Lamp " + unique(), "1250.00", 30));
    }

    // ------------------------------------------------------------------ helpers

    /** Places an order and pays it through the fake gateway. Returns the order number. */
    private String paidOrder(int quantity) throws Exception {
        String orderNumber = placeOrderFor(customer, addressId, productId, quantity);
        String init = mockMvc.perform(post("/api/v1/payments/" + orderNumber + "/initiate")
                        .header("Authorization", bearer(customer)))
                .andReturn().getResponse().getContentAsString();
        String paid = mockMvc.perform(post("/api/v1/payments/fake/pay")
                        .header("Authorization", bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("providerOrderId", JsonPath.read(init, "$.providerOrderId")))))
                .andReturn().getResponse().getContentAsString();
        mockMvc.perform(post("/api/v1/payments/verify")
                        .header("Authorization", bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paid))
                .andExpect(status().isOk());
        return orderNumber;
    }

    private ResultActions setStatus(String orderNumber, String target) throws Exception {
        return mockMvc.perform(patch("/api/v1/admin/orders/" + orderNumber + "/status")
                .header("Authorization", bearer(adminToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("status", target))));
    }

    private String dashboardJson() throws Exception {
        return mockMvc.perform(get("/api/v1/admin/dashboard").header("Authorization", bearer(adminToken())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private static BigDecimal money(String json, String path) {
        return new BigDecimal(JsonPath.read(json, path).toString());
    }

    // ------------------------------------------------------------------ dashboard

    @Test
    void dashboardReflectsAPaidOrder() throws Exception {
        String before = dashboardJson();
        paidOrder(3); // 3 x 1250 = 3750
        String after = dashboardJson();

        assertThat(money(after, "$.summary.revenue").subtract(money(before, "$.summary.revenue")))
                .isEqualByComparingTo("3750.00");
        assertThat(((Number) JsonPath.read(after, "$.summary.paidOrders")).longValue()
                - ((Number) JsonPath.read(before, "$.summary.paidOrders")).longValue()).isEqualTo(1);

        List<Object> days = JsonPath.read(after, "$.salesByDay");
        assertThat(days).hasSize(30);   // default range, every day present
        assertThat(money(after, "$.salesByDay[29].revenue").subtract(money(before, "$.salesByDay[29].revenue")))
                .isEqualByComparingTo("3750.00");   // today (IST)

        Map<String, Object> byStatus = JsonPath.read(after, "$.ordersByStatus");
        assertThat(byStatus).containsOnlyKeys("PLACED", "CONFIRMED", "SHIPPED", "DELIVERED", "CANCELLED");

        List<Integer> topIds = JsonPath.read(after, "$.topProducts[*].productId");
        assertThat(topIds.stream().map(Integer::longValue).toList()).contains(productId);
    }

    @Test
    void dashboardValidatesRange_andIsAdminOnly() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard?days=7").header("Authorization", bearer(adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.salesByDay.length()").value(7));
        mockMvc.perform(get("/api/v1/admin/dashboard?days=3").header("Authorization", bearer(adminToken())))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/admin/dashboard").header("Authorization", bearer(customer)))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ orders

    @Test
    void adminMovesOrderThroughTheLifecycle() throws Exception {
        String orderNumber = paidOrder(1);

        mockMvc.perform(get("/api/v1/admin/orders/" + orderNumber).header("Authorization", bearer(adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.customer.email").value(customerEmail))
                .andExpect(jsonPath("$.payments[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$.allowedTransitions").value(contains("SHIPPED", "CANCELLED")));

        setStatus(orderNumber, "SHIPPED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.shippedAt").value(notNullValue()))
                .andExpect(jsonPath("$.allowedTransitions").value(contains("DELIVERED")));

        setStatus(orderNumber, "CONFIRMED").andExpect(status().isConflict());   // only payment confirms
        setStatus(orderNumber, "CANCELLED").andExpect(status().isConflict());   // already shipped

        setStatus(orderNumber, "DELIVERED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowedTransitions").value(empty()));

        // The customer sees it, and got a status email
        mockMvc.perform(get("/api/v1/orders/" + orderNumber).header("Authorization", bearer(customer)))
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.deliveredAt").value(notNullValue()));
        awaitTrue("delivered email", () -> mockingDetails(mailSender).getInvocations().stream()
                .filter(i -> i.getMethod().getName().equals("send"))
                .anyMatch(i -> {
                    try {
                        return ("Order " + orderNumber + " is delivered").equals(((MimeMessage) i.getArgument(0)).getSubject());
                    } catch (Exception e) {
                        return false;
                    }
                }));
    }

    @Test
    void adminCancellingAPaidOrder_refundsAndRestocks() throws Exception {
        String orderNumber = paidOrder(4);

        setStatus(orderNumber, "CANCELLED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.status").value("CANCELLED"));

        awaitTrue("refund", () -> "REFUNDED".equals(jdbc.queryForObject("""
                SELECT p.status FROM payments p JOIN orders o ON o.id = p.order_id WHERE o.order_number = ?
                """, String.class, orderNumber)));
        mockMvc.perform(get("/api/v1/admin/products/" + productId).header("Authorization", bearer(adminToken())))
                .andExpect(jsonPath("$.availableQuantity").value(30));
    }

    @Test
    void orderSearchFiltersByStatusAndText() throws Exception {
        String shipped = paidOrder(1);
        setStatus(shipped, "SHIPPED").andExpect(status().isOk());
        String unpaid = placeOrderFor(customer, addressId, productId, 1);

        mockMvc.perform(get("/api/v1/admin/orders?q=" + customerEmail).header("Authorization", bearer(adminToken())))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].orderNumber").value(unpaid))   // newest first
                .andExpect(jsonPath("$.content[0].customerEmail").value(customerEmail));

        mockMvc.perform(get("/api/v1/admin/orders?status=SHIPPED&q=" + shipped)
                        .header("Authorization", bearer(adminToken())))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].status").value("SHIPPED"));

        mockMvc.perform(get("/api/v1/admin/orders?status=LOST").header("Authorization", bearer(adminToken())))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ customers

    @Test
    void customerListShowsOrderStats() throws Exception {
        paidOrder(2);                                         // 2500 paid
        placeOrderFor(customer, addressId, productId, 1);      // unpaid: counts as an order, not as spent

        mockMvc.perform(get("/api/v1/admin/customers?q=" + customerEmail).header("Authorization", bearer(adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].orderCount").value(2))
                .andExpect(jsonPath("$.content[0].totalSpent").value(2500.0))
                .andExpect(jsonPath("$.content[0].enabled").value(true));

        // Admins are not listed as customers
        mockMvc.perform(get("/api/v1/admin/customers?q=admin@northcart.test").header("Authorization", bearer(adminToken())))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void disablingACustomerBlocksThemImmediately() throws Exception {
        String body = mockMvc.perform(get("/api/v1/admin/customers?q=" + customerEmail)
                        .header("Authorization", bearer(adminToken())))
                .andReturn().getResponse().getContentAsString();
        long userId = ((Number) JsonPath.read(body, "$.content[0].id")).longValue();

        mockMvc.perform(patch("/api/v1/admin/customers/" + userId + "/status")
                        .header("Authorization", bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("enabled", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        // Existing token stops working on the very next request
        mockMvc.perform(get("/api/v1/cart").header("Authorization", bearer(customer)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(patch("/api/v1/admin/customers/" + userId + "/status")
                        .header("Authorization", bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("enabled", true))))
                .andExpect(status().isOk());
        assertThat(login(customerEmail, DEFAULT_PASSWORD)).isNotBlank();
    }

    @Test
    void adminAccountsCannotBeDisabled() throws Exception {
        Long adminId = jdbc.queryForObject("SELECT id FROM users WHERE email = 'admin@northcart.test'", Long.class);
        mockMvc.perform(patch("/api/v1/admin/customers/" + adminId + "/status")
                        .header("Authorization", bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("enabled", false))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Admin accounts can't be disabled here"));
    }
}
