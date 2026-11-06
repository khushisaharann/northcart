package com.northcart.cart.dto;

import com.northcart.cart.service.CartService;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Adding a product already in the cart increases that line's quantity. */
public record AddCartItemRequest(
        @Schema(example = "1") @NotNull Long productId,
        @Schema(example = "2") @NotNull @Min(1) @Max(CartService.MAX_QUANTITY_PER_ITEM) Integer quantity
) {
}
