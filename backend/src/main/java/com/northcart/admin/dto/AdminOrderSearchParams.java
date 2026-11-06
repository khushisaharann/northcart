package com.northcart.admin.dto;

import com.northcart.common.dto.PageParams;
import com.northcart.order.entity.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** GET /api/v1/admin/orders?status=CONFIRMED&q=khushi&page=0&size=20 */
public record AdminOrderSearchParams(
        @Schema(description = "Filter by status") OrderStatus status,
        @Schema(description = "Order number, customer name or email (contains)") @Size(max = 100) String q,
        @Min(0) Integer page,
        @Min(1) @Max(50) Integer size
) {
    public PageParams pageParams() {
        return new PageParams(page, size == null ? 20 : size);
    }
}
