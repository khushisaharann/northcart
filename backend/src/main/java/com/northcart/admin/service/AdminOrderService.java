package com.northcart.admin.service;

import com.northcart.admin.dto.AdminOrderDetailResponse;
import com.northcart.admin.dto.AdminOrderSearchParams;
import com.northcart.common.dto.PageResponse;
import com.northcart.order.dto.AdminOrderSummary;
import com.northcart.order.dto.AdminOrderView;
import com.northcart.order.entity.OrderStatus;
import com.northcart.order.service.OrderService;
import com.northcart.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Thin coordinator: order data from the order module, payments from the payment module. */
@Service
@RequiredArgsConstructor
public class AdminOrderService {

    private final OrderService orderService;
    private final PaymentService paymentService;

    public PageResponse<AdminOrderSummary> search(AdminOrderSearchParams params) {
        return orderService.searchForAdmin(params.status(), params.q(), params.pageParams());
    }

    @Transactional(readOnly = true)
    public AdminOrderDetailResponse get(String orderNumber) {
        return withPayments(orderService.getForAdmin(orderNumber));
    }

    @Transactional
    public AdminOrderDetailResponse changeStatus(String orderNumber, OrderStatus target) {
        return withPayments(orderService.changeStatusByAdmin(orderNumber, target));
    }

    private AdminOrderDetailResponse withPayments(AdminOrderView view) {
        return new AdminOrderDetailResponse(view.order(), view.customer(),
                paymentService.listForOrder(view.orderId()), view.allowedTransitions());
    }
}
