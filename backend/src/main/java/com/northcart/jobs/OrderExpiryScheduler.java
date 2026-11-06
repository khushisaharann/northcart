package com.northcart.jobs;

import com.northcart.order.service.OrderService;
import com.northcart.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Background job, every minute:
 * <ol>
 *   <li>Reconcile: for unpaid orders about to expire, ask the gateway whether the customer
 *       actually paid (e.g. closed the browser before our /verify call). Paid ones get confirmed.</li>
 *   <li>Expire: cancel the rest and release their stock.</li>
 * </ol>
 * Orders whose payment status couldn't be checked (gateway down) are skipped this round,
 * so we never cancel an order that might have been paid.
 *
 * <p>Lives in its own "jobs" package because it coordinates two modules.
 * Safe with several app instances (FOR UPDATE SKIP LOCKED). Disabled in tests.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.orders.expiry-job-enabled", havingValue = "true", matchIfMissing = true)
public class OrderExpiryScheduler {

    private final PaymentService paymentService;
    private final OrderService orderService;

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void run() {
        try {
            PaymentService.ReconciliationResult result = paymentService.reconcileExpiringPayments();
            Set<Long> skip = result.uncheckedOrderIds();
            int cancelled = orderService.expireUnpaidOrders(skip);
            if (result.confirmed() > 0 || cancelled > 0 || !skip.isEmpty()) {
                log.info("Expiry job: {} confirmed by reconciliation, {} cancelled, {} skipped",
                        result.confirmed(), cancelled, skip.size());
            }
        } catch (Exception e) {
            // Never let an exception kill the scheduler thread; try again next minute.
            log.error("Order expiry job failed", e);
        }
    }
}
