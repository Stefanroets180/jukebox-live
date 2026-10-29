package com.jukebox.live.repository;

import com.jukebox.live.entity.Party;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PartyRepository extends JpaRepository<Party, UUID> {
    Optional<Party> findByCode(String code);
    Optional<Party> findByIdAndIsActiveTrue(UUID id);
}
