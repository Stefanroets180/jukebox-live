package com.jukebox.live.repository;

import com.jukebox.live.entity.User;
import com.jukebox.live.entity.User.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
public class UserRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    @Test
    public void testDecrementCreditsIfSufficient_Success() {
        User user = User.builder()
                .username("testuser")
                .passwordHash("hash")
                .credits(5)
                .role(Role.USER)
                .build();
        entityManager.persist(user);
        entityManager.flush();

        int updated = userRepository.decrementCreditsIfSufficient(user.getId());

        assertThat(updated).isEqualTo(1);
        
        User updatedUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(updatedUser.getCredits()).isEqualTo(4);
    }

    @Test
    public void testDecrementCreditsIfSufficient_InsufficientCredits() {
        User user = User.builder()
                .username("testuser")
                .passwordHash("hash")
                .credits(0)
                .role(Role.USER)
                .build();
        entityManager.persist(user);
        entityManager.flush();

        int updated = userRepository.decrementCreditsIfSufficient(user.getId());

        assertThat(updated).isEqualTo(0);
        
        User unchangedUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(unchangedUser.getCredits()).isEqualTo(0);
    }
}
