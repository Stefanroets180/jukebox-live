package com.jukebox.live.repository;

import com.jukebox.live.entity.Party;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

public class QueueItemRepositoryImpl implements QueueItemRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    public Integer allocateNextPosition(UUID partyId) {
        // Lock the party row first to serialize queue operations per party
        // This works even when the queue is empty (no queue rows to lock)
        Party party = entityManager.find(Party.class, partyId, LockModeType.PESSIMISTIC_WRITE);
        
        // Now safely allocate the next position
        Integer maxPos = ((Number) entityManager
                .createNativeQuery("SELECT COALESCE(MAX(position), 0) FROM queue WHERE party_id = ?")
                .setParameter(1, partyId)
                .getSingleResult()).intValue();
        return maxPos + 1;
    }
}
