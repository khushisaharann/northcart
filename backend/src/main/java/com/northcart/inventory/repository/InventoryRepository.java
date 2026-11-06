package com.northcart.inventory.repository;

import com.northcart.inventory.entity.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findByProductId(Long productId);

    List<Inventory> findByProductIdIn(Collection<Long> productIds);

    boolean existsByProductId(Long productId);

    /**
     * Atomic, race-free stock change in ONE statement:
     * read-check-write happens inside the database row lock, so two concurrent requests
     * can't both pass the "enough stock?" check. Returns 1 if applied, 0 if it would go
     * negative (or the product has no inventory row).
     *
     * <p>Native SQL so the @Version column is incremented explicitly: bulk updates bypass
     * Hibernate's automatic optimistic-locking increment.</p>
     */
    @Modifying
    @Query(value = """
            UPDATE inventory
               SET quantity_available = quantity_available + :delta,
                   version = version + 1,
                   updated_at = :now
             WHERE product_id = :productId
               AND quantity_available + :delta >= 0
            """, nativeQuery = true)
    int adjustQuantity(@Param("productId") Long productId, @Param("delta") int delta, @Param("now") Instant now);
}
