package com.jukebox.live.service;

import com.jukebox.live.entity.CreditTransaction;
import com.jukebox.live.entity.Party;
import com.jukebox.live.entity.User;
import com.jukebox.live.entity.User.Role;
import com.jukebox.live.repository.CreditTransactionRepository;
import com.jukebox.live.repository.PartyRepository;
import com.jukebox.live.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;

/**
 * Integration test for concurrent credit spending using optimistic locking.
 *
 * This test uses @SpringBootTest to get full application context with proper proxy support
 * for self-injection in CreditService. It tests that optimistic locking via @Version on
 * CreditTransaction prevents concurrent overspending.
 *
 * Note: This test uses H2 in-memory database. While H2 correctly implements JPA @Version
 * optimistic locking, production uses PostgreSQL. The behavior should be identical as both
 * databases implement the JPA specification for @Version. For additional confidence, this test
 * should be run against a PostgreSQL instance in staging before production deployment.
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class CreditServiceConcurrencyIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PartyRepository partyRepository;

    @Autowired
    private CreditTransactionRepository transactionRepository;

    @Autowired
    private CreditService creditService;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void testConcurrentSpend_SameFinalCredit_AtMostOneSucceeds() throws InterruptedException {
        // Create a user with 0 credits (grant will add 1)
        String uniqueUsername = "concurrent-test-user-" + UUID.randomUUID().toString().substring(0, 8);
        User user = User.builder()
                .username(uniqueUsername)
                .passwordHash("hash")
                .credits(0)
                .role(Role.ADMIN)
                .build();
        userRepository.save(user);

        // Create a party (code must be 10 characters or less due to unique constraint)
        String uniquePartyCode = UUID.randomUUID().toString().substring(0, 10).toUpperCase();
        Party party = Party.builder()
                .code(uniquePartyCode)
                .hostUserId(user.getId())
                .partyIp("127.0.0.1")
                .latitude(0.0)
                .longitude(0.0)
                .creditsValidDays(30)
                .build();
        partyRepository.save(party);

        // Grant exactly 1 credit
        creditService.grantCredits(user.getId(), uniqueUsername, 1, party.getId());

        // Verify initial state: exactly one grant with 1 remaining credit, user has 1 credit
        var initialTransactions = transactionRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        List<CreditTransaction> initialGrants = initialTransactions.stream()
                .filter(t -> t.getType().equals("GRANT"))
                .toList();
        assertThat(initialGrants).hasSize(1);
        CreditTransaction initialGrant = initialGrants.get(0);
        assertThat(initialGrant.getRemainingCredits()).isEqualTo(1);
        
        User initialUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(initialUser.getCredits()).isEqualTo(1);

        // Try to spend concurrently from two threads
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        AtomicReference<Exception> thread0Exception = new AtomicReference<>();
        AtomicReference<Exception> thread1Exception = new AtomicReference<>();

        for (int i = 0; i < 2; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await(); // Wait for both threads to be ready
                    creditService.spendCredit(user.getId(), party.getId(), "Concurrent test thread " + threadId);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    if (threadId == 0) {
                        thread0Exception.set(e);
                    } else {
                        thread1Exception.set(e);
                    }
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Start both threads simultaneously
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        assertThat(completed).isTrue();

        // Exactly one should succeed, one should fail
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failureCount.get()).isEqualTo(1);

        // Verify the failing thread got an exception (could be optimistic locking or retry-related)
        Exception failingException = thread0Exception.get() != null ? thread0Exception.get() : thread1Exception.get();
        assertThat(failingException).isNotNull();
        // Log the exception for debugging
        System.out.println("Failing thread exception: " + failingException.getClass().getName() + ": " + failingException.getMessage());
        Throwable cause = failingException.getCause();
        while (cause != null) {
            System.out.println("  Caused by: " + cause.getClass().getName() + ": " + cause.getMessage());
            cause = cause.getCause();
        }

        // Verify final state
        var finalTransactions = transactionRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        List<CreditTransaction> spendTransactions = finalTransactions.stream()
                .filter(t -> t.getType().equals("SPEND"))
                .toList();

        // Exactly one spend transaction should exist
        assertThat(spendTransactions.size()).isEqualTo(1);

        // Grant should have 0 remaining credits (not overspent)
        CreditTransaction finalGrant = finalTransactions.stream()
                .filter(t -> t.getType().equals("GRANT"))
                .findFirst()
                .orElseThrow();
        assertThat(finalGrant.getRemainingCredits()).isEqualTo(0);

        // User balance should be exactly 0 (started with 0, grant added 1, spend decremented 1)
        User finalUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(finalUser.getCredits()).isEqualTo(0);
    }
}
