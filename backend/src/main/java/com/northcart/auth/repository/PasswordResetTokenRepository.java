package com.northcart.auth.repository;

import com.northcart.auth.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /**
     * Marks every still-unused token of a user as used, so only the newest emailed link
     * works and a link can't be reused after a successful reset.
     * A bulk JPQL UPDATE: one SQL statement, no entities loaded.
     */
    @Modifying
    @Query("""
            UPDATE PasswordResetToken t
               SET t.usedAt = :now, t.updatedAt = :now
             WHERE t.user.id = :userId AND t.usedAt IS NULL
            """)
    int invalidateActiveTokens(@Param("userId") Long userId, @Param("now") Instant now);
}
