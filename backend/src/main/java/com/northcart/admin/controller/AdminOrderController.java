package com.northcart.admin.controller;

import com.northcart.admin.dto.AdminOrderDetailResponse;
import com.northcart.admin.dto.AdminOrderSearchParams;
import com.northcart.admin.dto.UpdateOrderStatusRequest;
import com.northcart.admin.service.AdminOrderService;
import com.northcart.common.dto.PageResponse;
import com.northcart.config.OpenApiConfig;
import com.northcart.order.dto.AdminOrderSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin - Orders")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
@RestController
@RequestMapping("/api/v1/admin/orders")
@RequiredArgsConstructor
public class AdminOrderController {

    private final AdminOrderService adminOrderService;

    @Operation(summary = "All orders, newest first; filter by status and search order number / customer")
    @GetMapping
    public PageResponse<AdminOrderSummary> search(@Valid @ParameterObject AdminOrderSearchParams params) {
        return adminOrderService.search(params);
    }

    @Operation(summary = "Order details with customer, payment attempts and allowed next statuses")
    @GetMapping("/{orderNumber}")
    public AdminOrderDetailResponse get(@PathVariable String orderNumber) {
        return adminOrderService.get(orderNumber);
    }

    @Operation(summary = "Move an order: CONFIRMED -> SHIPPED -> DELIVERED, or CANCELLED (refunds if paid)")
    @PatchMapping("/{orderNumber}/status")
    public AdminOrderDetailResponse changeStatus(@PathVariable String orderNumber,
                                                 @Valid @RequestBody UpdateOrderStatusRequest request) {
        return adminOrderService.changeStatus(orderNumber, request.status());
    }
}
