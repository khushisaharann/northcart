package com.northcart.payment;

import com.jayway.jsonpath.JsonPath;
import com.northcart.AbstractIntegrationTest;
import com.northcart.order.service.OrderService;
import com.northcart.payment.service.PaymentService;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.mockingDetails;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Runs against the built-in FAKE gateway, which uses Razorpay's exact signature scheme. */
class PaymentIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private OrderService orderService;

    private String customer;
    private long addressId;
    private long productId;

    @BeforeEach
    void setUp() throws Exception {
        customer = registerCustomer(uniqueEmail());
        addressId = createAddress(customer);
        productId = id(createProduct(createCategory()[0], "Pay Speaker", "799.50", 20));
    }

    // ------------------------------------------------------------------ helpers

    private String order(int quantity) throws Exception {
        return placeOrderFor(customer, addressId, productId, quantity);
    }

    private String initiate(String token, String orderNumber) throws Exception {
        return mockMvc.perform(post("/api/v1/payments/" + orderNumber + "/initiate")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    /** Plays the customer completing the gateway popup. Returns {providerOrderId, providerPaymentId, signature}. */
    private Map<String, Object> payInPopup(String providerOrderId) throws Exception {
        String body = mockMvc.perform(post("/api/v1/payments/fake/pay")
                        .header("Authorization", bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("providerOrderId", providerOrderId))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Map<String, Object> simulated = JsonPath.read(body, "$");
        return new HashMap<>(simulated);
    }

    private ResultActions verify(String token, Map<String, Object> callback) throws Exception {
        return mockMvc.perform(post("/api/v1/payments/verify")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(callback)));
    }

    private String paymentStatus(String providerOrderId) {
        return jdbc.queryForObject("SELECT status FROM payments WHERE provider_order_id = ?",
                String.class, providerOrderId);
    }

    private String orderStatus(String orderNumber) {
        return jdbc.queryForObject("SELECT status FROM orders WHERE order_number = ?", String.class, orderNumber);
    }

    private List<String> sentSubjects() {
        try {
            return subjectsOfSentMails();
        } catch (RuntimeException e) {   // an async email arriving mid-read: just poll again
            return List.of();
        }
    }

    private List<String> subjectsOfSentMails() {
        return mockingDetails(mailSender).getInvocations().stream()
                .filter(inv -> inv.getMethod().getName().equals("send"))
                .map(inv -> {
                    try {
                        return ((MimeMessage) inv.getArgument(0)).getSubject();
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();
    }

    // ------------------------------------------------------------------ initiate

    @Test
    void initiate_returnsCheckoutOptions_andRetryReusesTheGatewayOrder() throws Exception {
        String orderNumber = order(2);   // 2 x 799.50 = 1599.00, free shipping

        String first = initiate(customer, orderNumber);
        assertThat((String) JsonPath.read(first, "$.provider")).isEqualTo("FAKE");
        assertThat((String) JsonPath.read(first, "$.keyId")).isEqualTo("rzp_test_fake");
        assertThat((String) JsonPath.read(first, "$.providerOrderId")).startsWith("order_fake_");
        assertThat(((Number) JsonPath.read(first, "$.amount")).longValue()).isEqualTo(159900L); // paise
        assertThat((String) JsonPath.read(first, "$.currency")).isEqualTo("INR");
        assertThat((String) JsonPath.read(first, "$.orderNumber")).isEqualTo(orderNumber);

        String second = initiate(customer, orderNumber);
        assertThat((String) JsonPath.read(second, "$.providerOrderId"))
                .isEqualTo(JsonPath.read(first, "$.providerOrderId"));
        Integer payments = jdbc.queryForObject(
                "SELECT count(*) FROM payments p JOIN orders o ON o.id = p.order_id WHERE o.order_number = ?",
                Integer.class, orderNumber);
        assertThat(payments).isEqualTo(1);
    }

    // ------------------------------------------------------------------ success path

    @Test
    void verifiedPayment_confirmsOrder_andReplayIsIdempotent() throws Exception {
        String orderNumber = order(1);
        String providerOrderId = JsonPath.read(initiate(customer, orderNumber), "$.providerOrderId");
        Map<String, Object> callback = payInPopup(providerOrderId);

        verify(customer, callback)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderNumber").value(orderNumber))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.confirmedAt").value(notNullValue()));
        assertThat(paymentStatus(providerOrderId)).isEqualTo("SUCCESS");

        // Same callback again (browser retry): same answer, nothing changes
        verify(customer, callback).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"));

        // A confirmed order can't be paid again
        mockMvc.perform(post("/api/v1/payments/" + orderNumber + "/initiate").header("Authorization", bearer(customer)))
                .andExpect(status().isConflict());
    }

    @Test
    void orderAndPaymentEmailsAreSent() throws Exception {
        String orderNumber = order(1);
        String providerOrderId = JsonPath.read(initiate(customer, orderNumber), "$.providerOrderId");
        verify(customer, payInPopup(providerOrderId)).andExpect(status().isOk());

        awaitTrue("order placed + payment emails", () -> {
            List<String> subjects = sentSubjects();
            return subjects.contains("Order " + orderNumber + " received")
                    && subjects.contains("Payment received for order " + orderNumber);
        });
    }

    // ------------------------------------------------------------------ never trust the client

    @Test
    void forgedOrTamperedSignatures_areRejected() throws Exception {
        String orderNumber = order(1);
        String providerOrderId = JsonPath.read(initiate(customer, orderNumber), "$.providerOrderId");
        Map<String, Object> callback = payInPopup(providerOrderId);

        Map<String, Object> forged = new HashMap<>(callback);
        forged.put("signature", "0".repeat(64));
        verify(customer, forged)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAYMENT_VERIFICATION_FAILED"));

        Map<String, Object> swappedPayment = new HashMap<>(callback);
        swappedPayment.put("providerPaymentId", "pay_someone_elses");
        verify(customer, swappedPayment).andExpect(status().isBadRequest());

        assertThat(orderStatus(orderNumber)).isEqualTo("PLACED");
        assertThat(paymentStatus(providerOrderId)).isEqualTo("PENDING");
    }

    @Test
    void customersCannotPayOrVerifyOthersOrders() throws Exception {
        String orderNumber = order(1);
        String providerOrderId = JsonPath.read(initiate(customer, orderNumber), "$.providerOrderId");
        Map<String, Object> callback = payInPopup(providerOrderId);
        String other = registerCustomer(uniqueEmail());

        mockMvc.perform(post("/api/v1/payments/" + orderNumber + "/initiate").header("Authorization", bearer(other)))
                .andExpect(status().isNotFound());
        verify(other, callback).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/payments/fake/pay").header("Authorization", bearer(other))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("providerOrderId", providerOrderId))))
                .andExpect(status().isNotFound());

        assertThat(orderStatus(orderNumber)).isEqualTo("PLACED");
    }

    // ------------------------------------------------------------------ refunds

    @Test
    void cancellingAPaidOrder_refundsThePayment() throws Exception {
        String orderNumber = order(1);
        String providerOrderId = JsonPath.read(initiate(customer, orderNumber), "$.providerOrderId");
        verify(customer, payInPopup(providerOrderId)).andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/orders/" + orderNumber + "/cancel").header("Authorization", bearer(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        awaitTrue("refund", () -> "REFUNDED".equals(paymentStatus(providerOrderId)));
        String refundId = jdbc.queryForObject("SELECT provider_refund_id FROM payments WHERE provider_order_id = ?",
                String.class, providerOrderId);
        assertThat(refundId).startsWith("rfnd_fake_");
    }

    @Test
    void paymentArrivingAfterCancellation_isRecordedAndRefunded() throws Exception {
        String orderNumber = order(1);
        String providerOrderId = JsonPath.read(initiate(customer, orderNumber), "$.providerOrderId");
        Map<String, Object> callback = payInPopup(providerOrderId);   // customer pays ...

        mockMvc.perform(patch("/api/v1/orders/" + orderNumber + "/cancel")   // ... but the order is cancelled first
                        .header("Authorization", bearer(customer)))
                .andExpect(status().isOk());
        assertThat(paymentStatus(providerOrderId)).isEqualTo("FAILED");

        verify(customer, callback)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_LATE"))
                .andExpect(jsonPath("$.message").value(startsWith("Order " + orderNumber + " was cancelled")));

        // Committed despite the 409 (noRollbackFor), then refunded after commit
        awaitTrue("late payment refund", () -> "REFUNDED".equals(paymentStatus(providerOrderId)));
        assertThat(orderStatus(orderNumber)).isEqualTo("CANCELLED");
    }

    // ------------------------------------------------------------------ expiry & reconciliation

    @Test
    void reconciliation_confirmsAPaidOrderWhoseVerifyCallNeverArrived() throws Exception {
        String orderNumber = order(1);
        String providerOrderId = JsonPath.read(initiate(customer, orderNumber), "$.providerOrderId");
        payInPopup(providerOrderId);   // paid at the gateway, but the browser closed: no /verify
        jdbc.update("UPDATE orders SET created_at = now() - interval '31 minutes' WHERE order_number = ?", orderNumber);

        // What OrderExpiryScheduler does every minute
        PaymentService.ReconciliationResult result = paymentService.reconcileExpiringPayments();
        orderService.expireUnpaidOrders(result.uncheckedOrderIds());

        assertThat(orderStatus(orderNumber)).isEqualTo("CONFIRMED");
        assertThat(paymentStatus(providerOrderId)).isEqualTo("SUCCESS");
    }

    @Test
    void unpaidOrderExpires_andItsPendingPaymentFails() throws Exception {
        String orderNumber = order(3);
        String providerOrderId = JsonPath.read(initiate(customer, orderNumber), "$.providerOrderId");
        jdbc.update("UPDATE orders SET created_at = now() - interval '31 minutes' WHERE order_number = ?", orderNumber);

        PaymentService.ReconciliationResult result = paymentService.reconcileExpiringPayments();
        orderService.expireUnpaidOrders(result.uncheckedOrderIds());

        assertThat(orderStatus(orderNumber)).isEqualTo("CANCELLED");
        assertThat(paymentStatus(providerOrderId)).isEqualTo("FAILED");
        mockMvc.perform(get("/api/v1/admin/products/" + productId).header("Authorization", bearer(adminToken())))
                .andExpect(jsonPath("$.availableQuantity").value(20)); // stock back
    }
}
