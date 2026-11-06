package com.northcart.admin.dto;

import com.northcart.order.dto.AdminOrderView;
import com.northcart.order.dto.OrderResponse;
import com.northcart.order.entity.OrderStatus;
import com.northcart.payment.dto.PaymentSummaryResponse;

import java.util.List;

public record AdminOrderDetailResponse(
        OrderResponse order,
        AdminOrderView.Customer customer,
        List<PaymentSummaryResponse> payments,
        List<OrderStatus> allowedTransitions   // buttons the admin UI should show
) {
}
