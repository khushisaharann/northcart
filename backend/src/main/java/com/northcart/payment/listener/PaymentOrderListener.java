package com.northcart.payment.listener;

import com.northcart.order.entity.OrderStatus;
import com.northcart.order.event.OrderStatusChangedEvent;
import com.northcart.payment.event.PaymentRefundRequestedEvent;
import com.northcart.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * The payment module reacting to order events. The order module never calls payment code,
 * so there is no circular dependency between the two.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentOrderListener {

    private final PaymentService paymentService;

    /**
     * Plain @EventListener = runs immediately, INSIDE the cancelling transaction. The order
     * becoming CANCELLED and its pending payment becoming FAILED commit together, or not at all.
     */
    @EventListener
    public void markPendingPaymentsFailed(OrderStatusChangedEvent event) {
        if (event.to() == OrderStatus.CANCELLED) {
            paymentService.onOrderCancelled(event.orderId());
        }
    }

    /**
     * Refunds call an external API, so they run AFTER the cancellation committed and on a
     * background thread: no database locks are held while waiting for the gateway.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void refundIfPaid(OrderStatusChangedEvent event) {
        if (event.to() != OrderStatus.CANCELLED) {
            return;
        }
        try {
            paymentService.refundSuccessfulPayments(event.orderId());
        } catch (Exception e) {
            log.error("Refund failed for order {}. Needs manual follow-up.", event.orderNumber(), e);
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void refundLatePayment(PaymentRefundRequestedEvent event) {
        try {
            paymentService.refundPayment(event.paymentId());
        } catch (Exception e) {
            log.error("Refund failed for payment id {}. Needs manual follow-up.", event.paymentId(), e);
        }
    }
}
