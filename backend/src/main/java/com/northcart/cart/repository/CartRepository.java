package com.northcart.cart.repository;

import com.northcart.cart.entity.Cart;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CartRepository extends JpaRepository<Cart, Long> {

    /**
     * Creates the cart only if the user doesn't have one yet, atomically.
     * A "find, then save if missing" in Java would let two parallel first requests both
     * try to insert, and one would fail on uk_carts_user.
     */
    @Modifying
    @Query(value = "INSERT INTO carts (user_id) VALUES (:userId) ON CONFLICT (user_id) DO NOTHING",
            nativeQuery = true)
    void insertIfAbsent(@Param("userId") Long userId);

    /**
     * SELECT ... FOR UPDATE: locks this user's cart row until the transaction ends, so
     * concurrent requests from the same user (double-click, two tabs) modify the cart one
     * after another instead of racing.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Cart c WHERE c.user.id = :userId")
    Optional<Cart> findByUserIdForUpdate(@Param("userId") Long userId);

    /**
     * Read path: cart + items + products in one query. Images and categories load lazily
     * but in batches (hibernate.default_batch_fetch_size), so no N+1. We do NOT also
     * fetch-join product.images: fetching two List collections at once throws
     * MultipleBagFetchException.
     */
    @Query("""
            SELECT c FROM Cart c
              LEFT JOIN FETCH c.items i
              LEFT JOIN FETCH i.product
             WHERE c.user.id = :userId
            """)
    Optional<Cart> findWithItemsByUserId(@Param("userId") Long userId);
}
