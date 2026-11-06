package com.northcart.notification.listener;

import com.northcart.order.dto.OrderEmailView;
import com.northcart.order.entity.OrderStatus;
import com.northcart.order.event.OrderPlacedEvent;
import com.northcart.order.event.OrderStatusChangedEvent;
import com.northcart.order.service.OrderService;
import com.northcart.notification.service.EmailService;
import com.northcart.payment.event.PaymentSucceededEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

/**
 * Order lifecycle emails. Same pattern as AuthEmailListener:
 * AFTER_COMMIT (only for things that really happened), @Async (never slows a request),
 * failures logged, never thrown.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEmailListener {

    private final OrderService orderService;
    private final EmailService emailService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPlaced(OrderPlacedEvent event) {
        send(event.orderId(), "Order " + event.orderNumber() + " received", "order-placed");
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentSucceeded(PaymentSucceededEvent event) {
        send(event.orderId(), null, "payment-received");
    }

    /** SHIPPED, DELIVERED, CANCELLED. (CONFIRMED is covered by the payment receipt.) */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStatusChanged(OrderStatusChangedEvent event) {
        if (event.to() == OrderStatus.CONFIRMED) {
            return;
        }
        send(event.orderId(), "Order " + event.orderNumber() + " is " + event.to().name().toLowerCase(),
                "order-status");
    }

    private void send(Long orderId, String subject, String template) {
        try {
            OrderEmailView order = orderService.getEmailView(orderId);
            String finalSubject = subject != null ? subject : "Payment received for order " + order.getOrderNumber();
            emailService.sendHtml(order.getCustomerEmail(), finalSubject, template, Map.of("order", order));
            log.info("Email '{}' sent for order {}", template, order.getOrderNumber());
        } catch (Exception e) {
            log.error("Failed to send '{}' email for order id {}", template, orderId, e);
        }
    }
}
