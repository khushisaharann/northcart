package com.northcart.order.mapper;

import com.northcart.order.dto.OrderItemResponse;
import com.northcart.order.dto.OrderResponse;
import com.northcart.order.dto.OrderSummaryResponse;
import com.northcart.order.dto.ShippingAddressResponse;
import com.northcart.order.entity.Order;
import com.northcart.order.entity.OrderItem;
import com.northcart.order.entity.ShippingAddress;

public final class OrderMapper {

    private static final int PREVIEW_ITEMS = 3;

    private OrderMapper() {
    }

    public static OrderResponse toResponse(Order o) {
        return new OrderResponse(
                o.getOrderNumber(),
                o.getStatus(),
                o.getItems().stream().map(OrderMapper::toItem).toList(),
                o.getSubtotal(),
                o.getShippingFee(),
                o.getTotal(),
                toAddress(o.getShippingAddress()),
                o.getCreatedAt(),
                o.getConfirmedAt(),
                o.getShippedAt(),
                o.getDeliveredAt(),
                o.getCancelledAt(),
                o.getStatus().isCancellable());
    }

    public static OrderSummaryResponse toSummary(Order o) {
        return new OrderSummaryResponse(
                o.getOrderNumber(),
                o.getStatus(),
                o.getTotalQuantity(),
                o.getItems().stream().limit(PREVIEW_ITEMS).map(OrderItem::getProductName).toList(),
                o.getTotal(),
                o.getCreatedAt());
    }

    private static OrderItemResponse toItem(OrderItem i) {
        return new OrderItemResponse(i.getProduct().getId(), i.getProduct().getSlug(), i.getProductName(),
                i.getSku(), i.getUnitPrice(), i.getQuantity(), i.getLineTotal());
    }

    private static ShippingAddressResponse toAddress(ShippingAddress a) {
        return new ShippingAddressResponse(a.getFullName(), a.getPhone(), a.getLine1(), a.getLine2(),
                a.getCity(), a.getState(), a.getPostalCode(), a.getCountry());
    }
}
