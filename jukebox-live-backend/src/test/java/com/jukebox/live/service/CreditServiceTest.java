package com.jukebox.live.service;

import com.jukebox.live.entity.CreditTransaction;
import com.jukebox.live.entity.User;
import com.jukebox.live.entity.User.Role;
import com.jukebox.live.repository.CreditTransactionRepository;
import com.jukebox.live.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;

@DataJpaTest
@ActiveProfiles("test")
public class CreditServiceTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        public CreditService creditService(UserRepository userRepository, CreditTransactionRepository transactionRepository, ApplicationEventPublisher eventPublisher) {
            return new CreditService(userRepository, transactionRepository, eventPublisher);
        }

        @Bean
        public ApplicationEventPublisher eventPublisher() {
            return mock(ApplicationEventPublisher.class);
        }
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CreditTransactionRepository transactionRepository;

    @Autowired
    private CreditService creditService;

    @Test
    public void testSpendCredit_InsufficientCredits_ThrowsException() {
        User user = User.builder()
                .username("testuser")
                .passwordHash("hash")
                .credits(0)
                .role(Role.USER)
                .build();
        userRepository.save(user);

        UUID partyId = UUID.randomUUID();

        assertThatThrownBy(() -> creditService.spendCredit(user.getId(), partyId, "Test"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Insufficient credits");

        // Verify no transaction was created
        assertThat(transactionRepository.findByUserIdOrderByCreatedAtDesc(user.getId())).isEmpty();

        // Verify credits unchanged
        User updatedUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(updatedUser.getCredits()).isEqualTo(0);
    }

    @Test
    public void testSpendCredit_Successful_DeductsCreditAndCreatesTransaction() {
        User user = User.builder()
                .username("testuser")
                .passwordHash("hash")
                .credits(5)
                .role(Role.USER)
                .build();
        userRepository.save(user);

        UUID partyId = UUID.randomUUID();

        creditService.spendCredit(user.getId(), partyId, "Test");

        // Verify credit deducted
        User updatedUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(updatedUser.getCredits()).isEqualTo(4);

        // Verify transaction created
        var transactions = transactionRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        assertThat(transactions).hasSize(1);
        CreditTransaction transaction = transactions.get(0);
        assertThat(transaction.getAmount()).isEqualTo(-1);
        assertThat(transaction.getType()).isEqualTo("SPEND");
        assertThat(transaction.getDescription()).isEqualTo("Test");
    }

    @Test
    public void testGrantCredits_Successful_IncreasesBalanceAndCreatesTransaction() {
        User guest = User.builder()
                .username("guestuser")
                .passwordHash("hash")
                .credits(5)
                .role(Role.USER)
                .build();
        userRepository.save(guest);

        User admin = User.builder()
                .username("adminuser")
                .passwordHash("hash")
                .credits(0)
                .role(Role.ADMIN)
                .build();
        userRepository.save(admin);

        UUID partyId = UUID.randomUUID();

        creditService.grantCredits(admin.getId(), "guestuser", 10, partyId);

        // Verify credit increased
        User updatedGuest = userRepository.findById(guest.getId()).orElseThrow();
        assertThat(updatedGuest.getCredits()).isEqualTo(15);

        // Verify transaction created
        var transactions = transactionRepository.findByUserIdOrderByCreatedAtDesc(guest.getId());
        assertThat(transactions).hasSize(1);
        CreditTransaction transaction = transactions.get(0);
        assertThat(transaction.getAmount()).isEqualTo(10);
        assertThat(transaction.getType()).isEqualTo("GRANT");
        assertThat(transaction.getDescription()).isEqualTo("Host granted 10 credits");
        assertThat(transaction.getPartyId()).isEqualTo(partyId);
    }

    @Test
    public void testGrantCredits_UserNotFound_ThrowsException() {
        User admin = User.builder()
                .username("adminuser")
                .passwordHash("hash")
                .credits(0)
                .role(Role.ADMIN)
                .build();
        userRepository.save(admin);

        UUID partyId = UUID.randomUUID();

        assertThatThrownBy(() -> creditService.grantCredits(admin.getId(), "nonexistent", 10, partyId))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("User not found");
    }
}
