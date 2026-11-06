package com.northcart.cart.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * <pre>
 * { "items": [...], "totalQuantity": 3, "subtotal": 1800.00,
 *   "shippingFee": 0.00, "total": 1800.00, "checkoutReady": true }
 * </pre>
 * checkoutReady is false when the cart is empty or any line has an issue; the frontend
 * disables the Checkout button. The server checks again at checkout, of course.
 */
public record CartResponse(
        List<CartItemResponse> items,
        int totalQuantity,
        BigDecimal subtotal,
        BigDecimal shippingFee,
        BigDecimal total,
        boolean checkoutReady
) {
}
