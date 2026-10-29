package com.jukebox.live.service;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaybackLeaseServiceTest {

    @Test
    void onlyOnePlayerInstanceCanOwnPartyPlayback() {
        PlaybackLeaseService service = new PlaybackLeaseService();
        UUID partyId = UUID.randomUUID();
        UUID hostUserId = UUID.randomUUID();
        UUID firstPlayerId = UUID.randomUUID();
        UUID secondPlayerId = UUID.randomUUID();

        assertTrue(service.acquire(partyId, hostUserId, firstPlayerId).isAcquired());
        assertFalse(service.acquire(partyId, hostUserId, secondPlayerId).isAcquired());
        assertTrue(service.isActive(partyId, hostUserId, firstPlayerId));
        assertFalse(service.isActive(partyId, hostUserId, secondPlayerId));
    }

    @Test
    void failedRenewalFromAnotherInstanceCannotClearTheActiveLease() {
        PlaybackLeaseService service = new PlaybackLeaseService();
        UUID partyId = UUID.randomUUID();
        UUID hostUserId = UUID.randomUUID();
        UUID firstPlayerId = UUID.randomUUID();
        UUID secondPlayerId = UUID.randomUUID();

        assertTrue(service.acquire(partyId, hostUserId, firstPlayerId).isAcquired());
        assertFalse(service.renew(partyId, hostUserId, secondPlayerId).isAcquired());
        assertTrue(service.isActive(partyId, hostUserId, firstPlayerId));
        assertFalse(service.acquire(partyId, hostUserId, secondPlayerId).isAcquired());
    }

    @Test
    void releasedLeaseCanBeClaimedByAnotherPlayer() {
        PlaybackLeaseService service = new PlaybackLeaseService();
        UUID partyId = UUID.randomUUID();
        UUID hostUserId = UUID.randomUUID();
        UUID firstPlayerId = UUID.randomUUID();
        UUID secondPlayerId = UUID.randomUUID();

        service.acquire(partyId, hostUserId, firstPlayerId);
        service.release(partyId, hostUserId, firstPlayerId);

        assertTrue(service.acquire(partyId, hostUserId, secondPlayerId).isAcquired());
    }
}
