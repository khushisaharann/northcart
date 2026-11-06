package com.northcart.payment.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.northcart.payment.config.PaymentProperties;
import com.northcart.payment.entity.PaymentProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * Razorpay over its REST API (https://razorpay.com/docs/api/), using Spring's RestClient.
 * No SDK: three HTTP calls and an HMAC are easier to read and explain than a library.
 *
 * <p>Test mode: use keys starting with rzp_test_ from the Razorpay dashboard. No real
 * money moves, and Razorpay provides test cards/UPI ids.</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "razorpay")
public class RazorpayPaymentGateway implements PaymentGateway {

    private static final String DEFAULT_BASE_URL = "https://api.razorpay.com/v1";

    private final RestClient client;
    private final String keyId;
    private final String keySecret;

    public RazorpayPaymentGateway(PaymentProperties properties) {
        PaymentProperties.Razorpay rp = properties.razorpay();
        if (rp == null || isBlank(rp.keyId()) || isBlank(rp.keySecret())) {
            throw new IllegalStateException(
                    "app.payment.provider=razorpay requires RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET");
        }
        this.keyId = rp.keyId();
        this.keySecret = rp.keySecret();

        // Never wait forever on a third party: a hung gateway must not hang our threads.
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(15));

        this.client = RestClient.builder()
                .baseUrl(isBlank(rp.baseUrl()) ? DEFAULT_BASE_URL : rp.baseUrl())
                .requestFactory(factory)
                .defaultHeaders(h -> h.setBasicAuth(keyId, keySecret)) // HTTP Basic: key_id:key_secret
                .build();
    }

    @Override
    public PaymentProvider provider() {
        return PaymentProvider.RAZORPAY;
    }

    @Override
    public String publicKeyId() {
        return keyId;
    }

    @Override
    public GatewayOrder createOrder(String receipt, BigDecimal amount, String currency) {
        try {
            JsonNode body = client.post().uri("/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "amount", PaymentSignatures.toMinorUnits(amount),
                            "currency", currency,
                            "receipt", receipt,
                            "notes", Map.of("orderNumber", receipt)))
                    .retrieve()
                    .body(JsonNode.class);
            return new GatewayOrder(body.get("id").asText());
        } catch (RestClientException | NullPointerException e) {
            throw new PaymentGatewayException("Razorpay create order failed for " + receipt, e);
        }
    }

    @Override
    public boolean verifyPaymentSignature(String providerOrderId, String providerPaymentId, String signature) {
        String expected = PaymentSignatures.razorpaySignature(providerOrderId, providerPaymentId, keySecret);
        return PaymentSignatures.constantTimeEquals(expected, signature);
    }

    @Override
    public Optional<String> findCapturedPaymentId(String providerOrderId) {
        try {
            JsonNode body = client.get().uri("/orders/{id}/payments", providerOrderId)
                    .retrieve()
                    .body(JsonNode.class);
            for (JsonNode payment : body.path("items")) {
                if ("captured".equals(payment.path("status").asText())) {
                    return Optional.of(payment.get("id").asText());
                }
            }
            return Optional.empty();
        } catch (RestClientException | NullPointerException e) {
            throw new PaymentGatewayException("Razorpay payment lookup failed for " + providerOrderId, e);
        }
    }

    @Override
    public String refund(String providerPaymentId, BigDecimal amount, String currency) {
        try {
            JsonNode body = client.post().uri("/payments/{id}/refund", providerPaymentId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("amount", PaymentSignatures.toMinorUnits(amount)))
                    .retrieve()
                    .body(JsonNode.class);
            return body.get("id").asText();
        } catch (RestClientException | NullPointerException e) {
            throw new PaymentGatewayException("Razorpay refund failed for " + providerPaymentId, e);
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
