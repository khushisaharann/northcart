package com.northcart.cart.dto;

import com.northcart.cart.service.CartService;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Sets the line to this exact quantity. To remove a line, use DELETE. */
public record UpdateCartItemRequest(
        @Schema(example = "3") @NotNull @Min(1) @Max(CartService.MAX_QUANTITY_PER_ITEM) Integer quantity
) {
}
