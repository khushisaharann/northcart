package com.northcart.payment.repository;

import com.northcart.payment.entity.Payment;
import com.northcart.payment.entity.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByProviderOrderId(String providerOrderId);

    /** Row lock for refunds: two refund triggers can't both refund the same payment. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") Long id);

    Optional<Payment> findFirstByOrderIdAndStatusOrderByCreatedAtDesc(Long orderId, PaymentStatus status);

    List<Payment> findByOrderIdAndStatus(Long orderId, PaymentStatus status);

    List<Payment> findByOrderIdOrderByCreatedAtDesc(Long orderId);

    /** Pending payments of PLACED orders that the expiry job is about to cancel. */
    @Query("""
            SELECT p FROM Payment p JOIN FETCH p.order o
             WHERE p.status = com.northcart.payment.entity.PaymentStatus.PENDING
               AND o.status = com.northcart.order.entity.OrderStatus.PLACED
               AND o.createdAt < :cutoff
            """)
    List<Payment> findPendingForExpiringOrders(@Param("cutoff") Instant cutoff);
}
