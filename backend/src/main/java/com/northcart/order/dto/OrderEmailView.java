package com.northcart.order.dto;

import com.northcart.order.entity.OrderStatus;
import lombok.Value;

import java.math.BigDecimal;
import java.util.List;

/**
 * Read-only data for order emails, so templates never touch lazy entities.
 * A Lombok @Value class (getters) rather than a record: Thymeleaf's expression language
 * reliably resolves ${order.total} through getTotal().
 */
@Value
public class OrderEmailView {
    String customerEmail;
    String customerName;
    String orderNumber;
    OrderStatus status;
    List<Line> lines;
    BigDecimal subtotal;
    BigDecimal shippingFee;
    BigDecimal total;
    String shippingAddress;
    String payByText;       // payment deadline, already formatted, e.g. "06 Nov 2026, 18:45 IST"
    String orderUrl;

    @Value
    public static class Line {
        String name;
        int quantity;
        BigDecimal lineTotal;
    }
}
