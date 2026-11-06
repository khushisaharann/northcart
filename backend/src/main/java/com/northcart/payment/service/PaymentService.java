package com.northcart.payment.service;

import com.northcart.common.exception.BusinessException;
import com.northcart.common.exception.ErrorCode;
import com.northcart.common.exception.InvalidStateException;
import com.northcart.common.exception.ResourceNotFoundException;
import com.northcart.config.AppProperties;
import com.northcart.order.dto.OrderResponse;
import com.northcart.order.entity.Order;
import com.northcart.order.entity.OrderStatus;
import com.northcart.order.service.OrderService;
import com.northcart.payment.config.PaymentProperties;
import com.northcart.payment.dto.PaymentInitResponse;
import com.northcart.payment.dto.PaymentSummaryResponse;
import com.northcart.payment.dto.VerifyPaymentRequest;
import com.northcart.payment.entity.Payment;
import com.northcart.payment.entity.PaymentStatus;
import com.northcart.payment.event.PaymentRefundRequestedEvent;
import com.northcart.payment.event.PaymentSucceededEvent;
import com.northcart.payment.gateway.PaymentGateway;
import com.northcart.payment.gateway.PaymentSignatures;
import com.northcart.user.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Payment flow (gateway-agnostic):
 * <pre>
 * 1. POST /payments/{orderNumber}/initiate   -> gateway order created, Payment row PENDING
 * 2. browser: customer pays in the gateway popup -> gets {paymentId, signature}
 * 3. POST /payments/verify                   -> signature checked HERE, Payment SUCCESS,
 *                                               order PLACED -> CONFIRMED, receipt email
 * </pre>
 * The rule that matters most: the client's word is never trusted. Only a valid HMAC
 * signature (or the gateway's own API) can mark something as paid.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentGateway gateway;
    private final com.northcart.payment.repository.PaymentRepository paymentRepository;
    private final OrderService orderService;
    private final PaymentProperties paymentProperties;
    private final AppProperties appProperties;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @PersistenceContext
    private EntityManager entityManager;

    // ------------------------------------------------------------------ 1. initiate

    @Transactional
    public PaymentInitResponse initiate(Long userId, String orderNumber) {
        // Order row lock: two tabs clicking "Pay" at once still create only ONE gateway order.
        Order order = orderService.lockOrderForPayment(userId, orderNumber);
        if (order.getStatus() != OrderStatus.PLACED) {
            throw new InvalidStateException("Order " + orderNumber + " is " + order.getStatus()
                    + " and can't be paid");
        }

        // Retry after a failed attempt (wrong OTP, card declined) reuses the same gateway
        // order: the gateway allows several attempts and pays the order at most once.
        Payment payment = paymentRepository
                .findFirstByOrderIdAndStatusOrderByCreatedAtDesc(order.getId(), PaymentStatus.PENDING)
                .orElseGet(() -> {
                    var gatewayOrder = gateway.createOrder(order.getOrderNumber(), order.getTotal(),
                            paymentProperties.currency());
                    log.info("Gateway order {} created for {}", gatewayOrder.id(), orderNumber);
                    return paymentRepository.save(new Payment(order, gateway.provider(), gatewayOrder.id(),
                            order.getTotal()));
                });

        User user = order.getUser();
        return new PaymentInitResponse(gateway.provider(), gateway.publicKeyId(), payment.getProviderOrderId(),
                PaymentSignatures.toMinorUnits(payment.getAmount()), paymentProperties.currency(),
                order.getOrderNumber(), user.getFullName(), user.getEmail(), user.getPhone());
    }

    // ------------------------------------------------------------------ 3. verify

    /**
     * noRollbackFor: a LATE payment is reported to the client as a 409 error, but the fact
     * that money arrived (and must be refunded) MUST still be committed.
     */
    @Transactional(noRollbackFor = LatePaymentException.class)
    public OrderResponse verify(Long userId, VerifyPaymentRequest request) {
        Payment payment = paymentRepository.findByProviderOrderId(request.providerOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment", request.providerOrderId()));

        // Every payment state change happens while holding its ORDER's row lock. Take the
        // lock, then re-read the payment: whatever we read before waiting may be outdated
        // (e.g. a parallel verify, or the expiry job cancelled the order meanwhile).
        Order order = orderService.lockOrderById(payment.getOrder().getId());
        if (!order.getUser().getId().equals(userId)) {
            throw new ResourceNotFoundException("Payment", request.providerOrderId()); // not yours: 404
        }
        entityManager.refresh(payment);

        if (!gateway.verifyPaymentSignature(request.providerOrderId(), request.providerPaymentId(),
                request.signature())) {
            log.warn("Invalid payment signature for {} (order {})", request.providerOrderId(), order.getOrderNumber());
            throw new BusinessException(HttpStatus.BAD_REQUEST, ErrorCode.PAYMENT_VERIFICATION_FAILED,
                    "Payment could not be verified");
        }

        // Idempotent: the same successful callback arriving twice (retry, double submit).
        if (payment.getStatus() == PaymentStatus.SUCCESS || payment.getStatus() == PaymentStatus.REFUNDED) {
            if (request.providerPaymentId().equals(payment.getProviderPaymentId())) {
                return orderService.toResponse(order);
            }
            throw new InvalidStateException("This order has already been paid");
        }

        Instant now = clock.instant();
        if (order.getStatus() == OrderStatus.PLACED && payment.getStatus() == PaymentStatus.PENDING) {
            payment.markSuccess(request.providerPaymentId(), now);
            orderService.confirmPaid(order);
            eventPublisher.publishEvent(new PaymentSucceededEvent(payment.getId(), order.getId()));
            log.info("Payment {} verified for order {}", request.providerPaymentId(), order.getOrderNumber());
            return orderService.toResponse(order);
        }

        // The order was cancelled/expired before this payment arrived: record it, refund it.
        payment.recordLateCapture(request.providerPaymentId(), now);
        eventPublisher.publishEvent(new PaymentRefundRequestedEvent(payment.getId()));
        log.warn("Late payment {} for {} order {}: refund requested",
                request.providerPaymentId(), order.getStatus(), order.getOrderNumber());
        throw new LatePaymentException(order.getOrderNumber());
    }

    // ------------------------------------------------------------------ cancellation & refunds

    /** Called synchronously, inside the cancelling transaction (see PaymentOrderListener). */
    @Transactional
    public void onOrderCancelled(Long orderId) {
        paymentRepository.findByOrderIdAndStatus(orderId, PaymentStatus.PENDING)
                .forEach(p -> p.markFailed("Order cancelled before the payment was completed"));
    }

    /** After a paid order is cancelled: refund its successful payment(s). Runs after commit. */
    @Transactional
    public void refundSuccessfulPayments(Long orderId) {
        paymentRepository.findByOrderIdAndStatus(orderId, PaymentStatus.SUCCESS)
                .forEach(p -> refundPayment(p.getId()));
    }

    /**
     * Locks the payment row first: if two triggers (cancellation and late capture) race,
     * the second one sees REFUNDED and does nothing. No double refunds.
     */
    @Transactional
    public void refundPayment(Long paymentId) {
        paymentRepository.findByIdForUpdate(paymentId).ifPresent(payment -> {
            entityManager.refresh(payment);
            if (payment.getStatus() == PaymentStatus.SUCCESS) {
                refund(payment);
            }
        });
    }

    private void refund(Payment payment) {
        String refundId = gateway.refund(payment.getProviderPaymentId(), payment.getAmount(),
                paymentProperties.currency());
        payment.markRefunded(refundId, clock.instant());
        log.info("Refunded payment {} ({}): refund {}", payment.getProviderPaymentId(), payment.getAmount(), refundId);
    }

    // ------------------------------------------------------------------ admin

    /** Every payment attempt of an order, newest first (admin order page). */
    @Transactional(readOnly = true)
    public java.util.List<PaymentSummaryResponse> listForOrder(Long orderId) {
        return paymentRepository.findByOrderIdOrderByCreatedAtDesc(orderId).stream()
                .map(p -> new PaymentSummaryResponse(p.getProvider(), p.getProviderOrderId(), p.getProviderPaymentId(),
                        p.getAmount(), p.getStatus(), p.getFailureReason(), p.getPaidAt(), p.getRefundedAt(),
                        p.getCreatedAt()))
                .toList();
    }

    // ------------------------------------------------------------------ reconciliation

    /**
     * @param confirmed         orders confirmed because the gateway says they were paid
     * @param uncheckedOrderIds orders we couldn't check (gateway error): don't expire them yet
     */
    public record ReconciliationResult(int confirmed, Set<Long> uncheckedOrderIds) {
    }

    /**
     * Safety net for "customer paid, but our /verify call never arrived" (closed tab, lost
     * network). Before an unpaid order expires, ask the gateway directly.
     */
    @Transactional
    public ReconciliationResult reconcileExpiringPayments() {
        Instant cutoff = clock.instant().minus(appProperties.orders().paymentTimeout());
        int confirmed = 0;
        Set<Long> unchecked = new HashSet<>();

        for (Payment payment : paymentRepository.findPendingForExpiringOrders(cutoff)) {
            Long orderId = payment.getOrder().getId();
            Optional<String> capturedId;
            try {
                capturedId = gateway.findCapturedPaymentId(payment.getProviderOrderId());
            } catch (RuntimeException e) {
                log.warn("Could not check payment {} with the gateway; skipping its order this round",
                        payment.getProviderOrderId(), e);
                unchecked.add(orderId);
                continue;
            }
            if (capturedId.isPresent()) {
                Order order = orderService.lockOrderById(orderId); // lock + fresh state
                entityManager.refresh(payment);
                if (order.getStatus() == OrderStatus.PLACED && payment.getStatus() == PaymentStatus.PENDING) {
                    payment.markSuccess(capturedId.get(), clock.instant());
                    orderService.confirmPaid(order);
                    eventPublisher.publishEvent(new PaymentSucceededEvent(payment.getId(), orderId));
                    log.info("Reconciliation confirmed order {} (payment {})",
                            order.getOrderNumber(), capturedId.get());
                    confirmed++;
                }
            }
        }
        return new ReconciliationResult(confirmed, unchecked);
    }

    // ------------------------------------------------------------------

    /** 409, but the transaction COMMITS (see noRollbackFor on verify). */
    public static class LatePaymentException extends BusinessException {
        public LatePaymentException(String orderNumber) {
            super(HttpStatus.CONFLICT, ErrorCode.PAYMENT_LATE, "Order " + orderNumber
                    + " was cancelled before your payment completed. The full amount will be refunded.");
        }
    }
}
