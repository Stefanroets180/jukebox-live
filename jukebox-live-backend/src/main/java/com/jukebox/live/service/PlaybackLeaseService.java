package com.jukebox.live.service;

import com.jukebox.live.dto.PlayerLeaseResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class PlaybackLeaseService {
    private static final Duration LEASE_DURATION = Duration.ofSeconds(30);

    private final Map<UUID, Lease> leases = new HashMap<>();

    public synchronized PlayerLeaseResponse acquire(UUID partyId, UUID hostUserId, UUID playerInstanceId) {
        Instant now = Instant.now();
        Lease current = leases.get(partyId);
        log.info("[LEASE ACQUIRE] partyId: {}, hostUserId: {}, playerInstanceId: {}, existingLease: {}, leaseExpired: {}",
            partyId, hostUserId, playerInstanceId,
            current != null,
            current == null || !current.expiresAt().isAfter(now));

        if (current != null && current.expiresAt().isAfter(now)
                && (!current.hostUserId().equals(hostUserId) || !current.playerInstanceId().equals(playerInstanceId))) {
            log.info("[LEASE ACQUIRE] REJECTED - another instance active - partyId: {}, currentHostUserId: {}, currentPlayerInstanceId: {}",
                partyId, current.hostUserId(), current.playerInstanceId());
            return PlayerLeaseResponse.builder()
                    .acquired(false)
                    .expiresAt(current.expiresAt())
                    .message("Another player instance is active for this party.")
                    .build();
        }

        Lease lease = new Lease(hostUserId, playerInstanceId, now.plus(LEASE_DURATION));
        leases.put(partyId, lease);
        log.info("[LEASE ACQUIRE] SUCCESS - partyId: {}, hostUserId: {}, playerInstanceId: {}, expiresAt: {}",
            partyId, hostUserId, playerInstanceId, lease.expiresAt());
        return PlayerLeaseResponse.builder()
                .acquired(true)
                .expiresAt(lease.expiresAt())
                .message("Player lease acquired.")
                .build();
    }

    public synchronized PlayerLeaseResponse renew(UUID partyId, UUID hostUserId, UUID playerInstanceId) {
        Lease current = leases.get(partyId);
        Instant now = Instant.now();
        log.info("[LEASE RENEW] partyId: {}, hostUserId: {}, playerInstanceId: {}, leaseExists: {}, leaseExpired: {}",
            partyId, hostUserId, playerInstanceId,
            current != null,
            current == null || !current.expiresAt().isAfter(now));

        if (current == null || !current.expiresAt().isAfter(now)) {
            leases.remove(partyId);
            log.info("[LEASE RENEW] REJECTED - lease missing or expired - partyId: {}", partyId);
            return PlayerLeaseResponse.builder()
                    .acquired(false)
                    .message("Player lease expired or no longer exists.")
                    .build();
        }
        if (!current.hostUserId().equals(hostUserId)
                || !current.playerInstanceId().equals(playerInstanceId)) {
            log.info("[LEASE RENEW] REJECTED - identity mismatch - partyId: {}, currentHostUserId: {}, currentPlayerInstanceId: {}",
                partyId, current.hostUserId(), current.playerInstanceId());
            return PlayerLeaseResponse.builder()
                    .acquired(false)
                    .expiresAt(current.expiresAt())
                    .message("Player lease belongs to another instance.")
                    .build();
        }

        Lease renewed = new Lease(hostUserId, playerInstanceId, now.plus(LEASE_DURATION));
        leases.put(partyId, renewed);
        log.info("[LEASE RENEW] SUCCESS - partyId: {}, hostUserId: {}, playerInstanceId: {}, expiresAt: {}",
            partyId, hostUserId, playerInstanceId, renewed.expiresAt());
        return PlayerLeaseResponse.builder()
                .acquired(true)
                .expiresAt(renewed.expiresAt())
                .message("Player lease renewed.")
                .build();
    }

    public synchronized void release(UUID partyId, UUID hostUserId, UUID playerInstanceId) {
        Lease current = leases.get(partyId);
        log.info("[LEASE RELEASE] partyId: {}, hostUserId: {}, playerInstanceId: {}, leaseExists: {}, identityMatch: {}",
            partyId, hostUserId, playerInstanceId,
            current != null,
            current != null && current.hostUserId().equals(hostUserId) && current.playerInstanceId().equals(playerInstanceId));

        if (current != null && current.hostUserId().equals(hostUserId)
                && current.playerInstanceId().equals(playerInstanceId)) {
            leases.remove(partyId);
            log.info("[LEASE RELEASE] SUCCESS - lease removed - partyId: {}", partyId);
        } else {
            log.info("[LEASE RELEASE] NOOP - lease not removed - partyId: {}", partyId);
        }
    }

    public synchronized boolean isActive(UUID partyId, UUID hostUserId, UUID playerInstanceId) {
        Lease current = leases.get(partyId);
        if (current == null || !current.expiresAt().isAfter(Instant.now())) {
            leases.remove(partyId);
            return false;
        }
        return current.hostUserId().equals(hostUserId)
                && current.playerInstanceId().equals(playerInstanceId);
    }

    private record Lease(UUID hostUserId, UUID playerInstanceId, Instant expiresAt) {}
}
