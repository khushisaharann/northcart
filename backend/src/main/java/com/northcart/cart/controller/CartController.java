package com.northcart.cart.controller;

import com.northcart.cart.dto.AddCartItemRequest;
import com.northcart.cart.dto.CartResponse;
import com.northcart.cart.dto.UpdateCartItemRequest;
import com.northcart.cart.service.CartService;
import com.northcart.config.OpenApiConfig;
import com.northcart.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Every endpoint returns the WHOLE updated cart, so the frontend replaces its cart state
 * with the response and never has to recalculate totals itself.
 * CUSTOMER role only (SecurityConfig: /api/v1/cart/**).
 */
@Tag(name = "Cart")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @Operation(summary = "My cart with live prices, totals and stock warnings")
    @GetMapping
    public CartResponse getCart(@AuthenticationPrincipal AuthenticatedUser me) {
        return cartService.getCart(me.getId());
    }

    @Operation(summary = "Add a product (adds to the quantity if it is already in the cart)")
    @PostMapping("/items")
    public CartResponse addItem(@AuthenticationPrincipal AuthenticatedUser me,
                                @Valid @RequestBody AddCartItemRequest request) {
        return cartService.addItem(me.getId(), request);
    }

    @Operation(summary = "Set the quantity of a cart line")
    @PatchMapping("/items/{itemId}")
    public CartResponse updateItem(@AuthenticationPrincipal AuthenticatedUser me,
                                   @PathVariable Long itemId,
                                   @Valid @RequestBody UpdateCartItemRequest request) {
        return cartService.updateItem(me.getId(), itemId, request);
    }

    @Operation(summary = "Remove a line from the cart")
    @DeleteMapping("/items/{itemId}")
    public CartResponse removeItem(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long itemId) {
        return cartService.removeItem(me.getId(), itemId);
    }
}
