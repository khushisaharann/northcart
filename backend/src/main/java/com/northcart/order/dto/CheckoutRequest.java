package com.northcart.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Only the address is chosen by the client. Items, quantities and PRICES come from the
 * server-side cart and catalog; the client can't send its own prices.
 */
public record CheckoutRequest(
        @Schema(example = "1", description = "One of my saved addresses") @NotNull Long addressId
) {
}
