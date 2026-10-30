package com.jukebox.live.service;

import com.jukebox.live.entity.CreditTransaction;
import com.jukebox.live.entity.User;
import com.jukebox.live.entity.Party;
import com.jukebox.live.repository.PartyRepository;
import com.jukebox.live.event.CreditUpdatedEvent;
import com.jukebox.live.repository.CreditTransactionRepository;
import com.jukebox.live.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CreditService {

    private final UserRepository userRepository;
    private final PartyRepository partyRepository;
    private final CreditTransactionRepository transactionRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Lazy
    private CreditService self;

    @Transactional
    public void grantCredits(UUID adminUserId, String username, Integer amount, UUID partyId) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        if (amount == null || amount < 1 || amount > 100) {
            throw new IllegalArgumentException("Credit amount must be between 1 and 100");
        }
        Party party = partyRepository.findById(partyId).orElseThrow(() -> new RuntimeException("Party not found"));
        Instant now = Instant.now();
        user.setCredits(user.getCredits() + amount);
        userRepository.save(user);

        transactionRepository.save(CreditTransaction.builder()
                .userId(user.getId())
                .partyId(partyId)
                .amount(amount)
                .remainingCredits(amount)
                .expiresAt(now.plus(party.getCreditsValidDays(), ChronoUnit.DAYS))
                .type("GRANT")
                .description("Host granted " + amount + " credits")
                .createdAt(now)
                .build());

        // Publish event to broadcast credit update to guest via WebSocket
        eventPublisher.publishEvent(new CreditUpdatedEvent(this, user.getId(), user.getCredits()));
    }

    @Transactional
    public void spendCredit(UUID userId, UUID partyId, String description) {
        spendCreditInternal(userId, partyId, description, 0);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void spendCreditRetry(UUID userId, UUID partyId, String description, int retryCount) {
        spendCreditInternal(userId, partyId, description, retryCount);
    }

    private void spendCreditInternal(UUID userId, UUID partyId, String description, int retryCount) {
        final int MAX_RETRIES = 3;
        Instant now = Instant.now();
        List<CreditTransaction> grants = transactionRepository.findSpendableGrants(userId, now);

        // If no grants exist (e.g., old data before migration), fall back to legacy behavior
        if (grants.isEmpty()) {
            int updated = userRepository.decrementCreditsIfSufficient(userId);
            if (updated == 0) {
                throw new RuntimeException("Insufficient credits");
            }
            transactionRepository.save(CreditTransaction.builder()
                    .userId(userId)
                    .partyId(partyId)
                    .amount(-1)
                    .type("SPEND")
                    .description(description)
                    .createdAt(now)
                    .build());
            return;
        }

        // Grant-based spending: select earliest-expiring grant and decrement its remaining credits
        // Optimistic locking via @Version on CreditTransaction entity prevents concurrent overspending
        CreditTransaction grant = grants.stream().findFirst()
                .orElseThrow(() -> new RuntimeException("Insufficient credits"));

        if (grant.getRemainingCredits() <= 0) {
            throw new RuntimeException("Insufficient credits");
        }
        grant.setRemainingCredits(grant.getRemainingCredits() - 1);

        try {
            CreditTransaction saved = transactionRepository.save(grant);
            transactionRepository.flush();
        } catch (ObjectOptimisticLockingFailureException e) {
            // Concurrent modification occurred - another transaction updated this grant
            // Retry with a fresh transaction using REQUIRES_NEW propagation
            if (retryCount >= MAX_RETRIES) {
                log.error("Max retries ({}) reached for concurrent credit spend on grant {}", MAX_RETRIES, grant.getId());
                throw new RuntimeException("Concurrent credit spend detected, please retry", e);
            }
            log.warn("Concurrent modification detected for grant {}, retrying (attempt {})", grant.getId(), retryCount + 1);
            self.spendCreditRetry(userId, partyId, description, retryCount + 1);
            return;
        }

        // Only decrement user credits after grant update succeeds to avoid double-decrement on retry
        int updated = userRepository.decrementCreditsIfSufficient(userId);
        if (updated == 0) {
            // Roll back grant update by throwing exception
            throw new RuntimeException("Insufficient credits");
        }

        transactionRepository.save(CreditTransaction.builder()
                .userId(userId).partyId(partyId).amount(-1).grantId(grant.getId())
                .remainingCredits(0).type("SPEND").description(description).createdAt(now).build());
    }
}
