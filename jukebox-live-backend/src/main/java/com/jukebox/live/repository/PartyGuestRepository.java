package com.jukebox.live.repository;

import com.jukebox.live.entity.PartyGuest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PartyGuestRepository extends JpaRepository<PartyGuest, PartyGuest.PartyGuestId> {
    List<PartyGuest> findByPartyId(UUID partyId);
    boolean existsByPartyIdAndUserId(UUID partyId, UUID userId);
}
