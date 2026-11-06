package com.northcart.admin.dto;

import com.northcart.order.entity.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record UpdateOrderStatusRequest(
        @Schema(example = "SHIPPED", allowableValues = {"SHIPPED", "DELIVERED", "CANCELLED"}) @NotNull OrderStatus status
) {
}
