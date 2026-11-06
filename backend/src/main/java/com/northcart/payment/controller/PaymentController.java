package com.northcart.payment.controller;

import com.northcart.config.OpenApiConfig;
import com.northcart.order.dto.OrderResponse;
import com.northcart.payment.dto.PaymentInitResponse;
import com.northcart.payment.dto.VerifyPaymentRequest;
import com.northcart.payment.service.PaymentService;
import com.northcart.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** CUSTOMER role only (SecurityConfig: /api/v1/payments/**). */
@Tag(name = "Payments")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @Operation(summary = "Start paying a PLACED order: returns the options for the gateway checkout popup")
    @PostMapping("/{orderNumber}/initiate")
    public PaymentInitResponse initiate(@AuthenticationPrincipal AuthenticatedUser me,
                                        @PathVariable String orderNumber) {
        return paymentService.initiate(me.getId(), orderNumber);
    }

    @Operation(summary = "Verify the gateway's signature and confirm the order",
            description = "400 PAYMENT_VERIFICATION_FAILED for a bad signature; 409 PAYMENT_LATE if the order "
                    + "was cancelled before the payment arrived (it is refunded automatically).")
    @PostMapping("/verify")
    public OrderResponse verify(@AuthenticationPrincipal AuthenticatedUser me,
                                @Valid @RequestBody VerifyPaymentRequest request) {
        return paymentService.verify(me.getId(), request);
    }
}
