package com.northcart.payment.controller;

import com.northcart.common.exception.ResourceNotFoundException;
import com.northcart.config.OpenApiConfig;
import com.northcart.payment.dto.FakeCheckoutRequest;
import com.northcart.payment.gateway.FakePaymentGateway;
import com.northcart.payment.repository.PaymentRepository;
import com.northcart.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * DEV/TEST ONLY: plays the role of the Razorpay checkout popup when app.payment.provider=fake.
 * The bean doesn't exist at all with the real Razorpay provider.
 *
 * <p>Returns what the popup would give the browser on success; the browser then calls
 * /payments/verify with it, exactly as with Razorpay.</p>
 */
@Tag(name = "Payments (fake gateway)")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
@RestController
@RequestMapping("/api/v1/payments/fake")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "fake", matchIfMissing = true)
public class FakeCheckoutController {

    private final FakePaymentGateway fakeGateway;
    private final PaymentRepository paymentRepository;

    @Operation(summary = "Simulate a successful payment in the gateway popup (fake provider only)")
    @PostMapping("/pay")
    @Transactional(readOnly = true)
    public FakePaymentGateway.SimulatedPayment pay(@AuthenticationPrincipal AuthenticatedUser me,
                                                   @Valid @RequestBody FakeCheckoutRequest request) {
        // Only your own gateway orders can be "paid".
        paymentRepository.findByProviderOrderId(request.providerOrderId())
                .filter(p -> p.getOrder().getUser().getId().equals(me.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Payment", request.providerOrderId()));
        return fakeGateway.simulateSuccessfulPayment(request.providerOrderId());
    }
}
