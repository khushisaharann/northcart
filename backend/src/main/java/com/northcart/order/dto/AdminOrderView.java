package com.northcart.order.dto;

import com.northcart.order.entity.OrderStatus;

import java.util.List;

/** Order details for admins: the customer's view + who ordered + what the admin can do next. */
public record AdminOrderView(
        Long orderId,
        OrderResponse order,
        Customer customer,
        List<OrderStatus> allowedTransitions
) {
    public record Customer(Long id, String fullName, String email, String phone) {
    }
}
