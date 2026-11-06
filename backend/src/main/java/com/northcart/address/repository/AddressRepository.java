package com.northcart.address.repository;

import com.northcart.address.entity.Address;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AddressRepository extends JpaRepository<Address, Long> {

    /** Default address first, then newest. Uses ix_addresses_user_id. */
    List<Address> findByUserIdOrderByDefaultAddressDescCreatedAtDesc(Long userId);

    long countByUserId(Long userId);

    /** Ownership check built into the query: another user's address id simply isn't found. */
    Optional<Address> findByIdAndUserId(Long id, Long userId);

    /**
     * Runs immediately as SQL, BEFORE the new default address is inserted.
     *
     * <p>Why not just load the old default and call setDefaultAddress(false)? Hibernate
     * flushes INSERTs before UPDATEs, so the new row (is_default = true) would be inserted
     * while the old one is still true, violating ux_addresses_one_default_per_user.</p>
     */
    @Modifying
    @Query("""
            UPDATE Address a
               SET a.defaultAddress = false, a.updatedAt = :now
             WHERE a.user.id = :userId AND a.defaultAddress = true
            """)
    int clearDefaultForUser(@Param("userId") Long userId, @Param("now") Instant now);
}
