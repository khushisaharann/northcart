package com.northcart.order.repository;

import com.northcart.order.entity.Order;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {

    Optional<Order> findByOrderNumber(String orderNumber);

    /** "My orders", newest first. Served by ix_orders_user_created_at. */
    Page<Order> findByUserId(Long userId, Pageable pageable);

    /** Scoped by user: another customer's order number is simply "not found". */
    @EntityGraph(attributePaths = "items")
    Optional<Order> findByOrderNumberAndUserId(String orderNumber, Long userId);

    Optional<Order> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

    /** Locks the order row so two cancellations can't both release the stock. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.orderNumber = :orderNumber AND o.user.id = :userId")
    Optional<Order> findForUpdate(@Param("orderNumber") String orderNumber, @Param("userId") Long userId);

    /**
     * Picks a batch of unpaid orders past their deadline and LOCKS them.
     * SKIP LOCKED: if two app instances run the job at the same time, each takes different
     * rows instead of waiting for (or double-processing) the other's.
     */
    @Query(value = """
            SELECT id FROM orders
             WHERE status = 'PLACED' AND created_at < :cutoff
             ORDER BY created_at
             LIMIT :batchSize
               FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<Long> lockExpiredPlacedOrderIds(@Param("cutoff") Instant cutoff, @Param("batchSize") int batchSize);
}
