package com.northcart.payment.gateway;

/** A payment order created at the gateway (Razorpay: "order_Pxyz..."). */
public record GatewayOrder(String id) {
}
