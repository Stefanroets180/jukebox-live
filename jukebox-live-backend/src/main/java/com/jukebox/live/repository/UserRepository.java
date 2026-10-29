package com.jukebox.live.repository;

import com.jukebox.live.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
    boolean existsByRole(User.Role role);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE User u SET u.credits = u.credits - 1 WHERE u.id = :userId AND u.credits >= 1")
    @Transactional
    int decrementCreditsIfSufficient(UUID userId);
}
