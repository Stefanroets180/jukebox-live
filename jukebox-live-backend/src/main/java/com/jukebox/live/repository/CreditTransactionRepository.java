package com.jukebox.live.repository;

import com.jukebox.live.entity.CreditTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface CreditTransactionRepository extends JpaRepository<CreditTransaction, UUID> {
    List<CreditTransaction> findByUserIdOrderByCreatedAtDesc(UUID userId);

    // Find spendable grants ordered by earliest expiry first
    // Optimistic locking via @Version on CreditTransaction entity prevents concurrent overspending
    // For PostgreSQL production, consider adding native query with FOR UPDATE for additional protection
    @Query("select c from CreditTransaction c where c.userId = :userId and c.type = 'GRANT' and c.remainingCredits > 0 and c.expiresAt > :now order by c.expiresAt asc, c.createdAt asc, c.id asc")
    List<CreditTransaction> findSpendableGrants(UUID userId, Instant now);
}
