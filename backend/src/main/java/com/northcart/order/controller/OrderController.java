package com.northcart.order.controller;

import com.northcart.common.dto.PageParams;
import com.northcart.common.dto.PageResponse;
import com.northcart.common.exception.BusinessException;
import com.northcart.common.exception.ErrorCode;
import com.northcart.config.OpenApiConfig;
import com.northcart.order.dto.CheckoutRequest;
import com.northcart.order.dto.OrderResponse;
import com.northcart.order.dto.OrderSummaryResponse;
import com.northcart.order.service.OrderService;
import com.northcart.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** CUSTOMER role only (SecurityConfig: /api/v1/orders/**). */
@Tag(name = "Orders")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
    private static final int MAX_KEY_LENGTH = 64;

    private final OrderService orderService;

    @Operation(summary = "Checkout: turn my cart into an order and reserve the stock",
            description = "Send a unique Idempotency-Key (e.g. a UUID) per checkout attempt. Re-sending the "
                    + "same key returns the existing order (200) instead of creating a duplicate (201).")
    @PostMapping
    public ResponseEntity<OrderResponse> checkout(
            @AuthenticationPrincipal AuthenticatedUser me,
            @Parameter(description = "Unique per checkout attempt, max 64 chars")
            @RequestHeader(name = IDEMPOTENCY_HEADER, required = false) String idempotencyKey,
            @Valid @RequestBody CheckoutRequest request) {

        if (idempotencyKey != null && (idempotencyKey.isBlank() || idempotencyKey.length() > MAX_KEY_LENGTH)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, ErrorCode.MALFORMED_REQUEST,
                    IDEMPOTENCY_HEADER + " must be 1-" + MAX_KEY_LENGTH + " characters");
        }
        OrderService.CheckoutResult result = orderService.placeOrder(me.getId(), request, idempotencyKey);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.order());
    }

    @Operation(summary = "My order history, newest first")
    @GetMapping
    public PageResponse<OrderSummaryResponse> list(@AuthenticationPrincipal AuthenticatedUser me,
                                                   @Valid @ParameterObject PageParams params) {
        return orderService.listForUser(me.getId(), params);
    }

    @Operation(summary = "Order details and tracking timeline")
    @GetMapping("/{orderNumber}")
    public OrderResponse get(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable String orderNumber) {
        return orderService.getForUser(me.getId(), orderNumber);
    }

    @Operation(summary = "Cancel my order (only while PLACED or CONFIRMED); stock is returned")
    @PatchMapping("/{orderNumber}/cancel")
    public OrderResponse cancel(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable String orderNumber) {
        return orderService.cancelByCustomer(me.getId(), orderNumber);
    }
}
