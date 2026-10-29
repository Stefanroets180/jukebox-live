package com.jukebox.live.service;

import com.jukebox.live.entity.CreditTransaction;
import com.jukebox.live.entity.User;
import com.jukebox.live.event.CreditUpdatedEvent;
import com.jukebox.live.repository.CreditTransactionRepository;
import com.jukebox.live.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CreditService {

    private final UserRepository userRepository;
    private final CreditTransactionRepository transactionRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void grantCredits(UUID adminUserId, String username, Integer amount, UUID partyId) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setCredits(user.getCredits() + amount);
        userRepository.save(user);

        transactionRepository.save(CreditTransaction.builder()
                .userId(user.getId())
                .partyId(partyId)
                .amount(amount)
                .type("GRANT")
                .description("Host granted " + amount + " credits")
                .build());

        // Publish event to broadcast credit update to guest via WebSocket
        eventPublisher.publishEvent(new CreditUpdatedEvent(this, user.getId(), user.getCredits()));
    }

    @Transactional
    public void spendCredit(UUID userId, UUID partyId, String description) {
        // Atomic decrement - only succeeds if credits >= 1
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
                .build());
    }
}
