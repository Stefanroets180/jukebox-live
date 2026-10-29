package com.jukebox.live.event;

import com.jukebox.live.entity.QueueItem;
import com.jukebox.live.repository.QueueItemRepository;
import com.jukebox.live.service.PlaybackService;
import com.jukebox.live.service.QueueService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class BroadcastEventListener {

    private static final Logger log = LoggerFactory.getLogger(BroadcastEventListener.class);
    private final QueueService queueService;
    private final PlaybackService playbackService;
    private final QueueItemRepository queueItemRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleQueueUpdated(QueueUpdatedEvent event) {
        try {
            queueService.broadcastQueueUpdate(event.getPartyId());
        } catch (Exception e) {
            // Log but don't fail - transaction is already committed
            log.error("Failed to broadcast queue update for party {}", event.getPartyId(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleCreditUpdated(CreditUpdatedEvent event) {
        try {
            queueService.broadcastCreditUpdate(event.getUserId(), event.getCredits());
        } catch (Exception e) {
            // Log but don't fail - transaction is already committed
            log.error("Failed to broadcast credit update for user {}", event.getUserId(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleArtworkUpdated(ArtworkUpdatedEvent event) {
        try {
            List<QueueItem> affectedItems = queueItemRepository.findByTrackIdAndStatusIn(
                    event.getTrackId(), List.of(QueueItem.Status.PENDING, QueueItem.Status.PLAYING));
            Set<UUID> partyIds = new LinkedHashSet<>();
            affectedItems.forEach(item -> partyIds.add(item.getPartyId()));

            for (UUID partyId : partyIds) {
                queueService.broadcastQueueUpdate(partyId);
                queueItemRepository.findFirstByPartyIdAndStatusOrderByPositionAsc(partyId, QueueItem.Status.PLAYING)
                        .filter(item -> event.getTrackId().equals(item.getTrackId()))
                        .ifPresent(item -> playbackService.broadcastPlaybackUpdate(
                                partyId, item.getId(), !Boolean.TRUE.equals(item.getIsPaused())));
            }
        } catch (Exception e) {
            log.error("Failed to broadcast artwork update for track {}", event.getTrackId(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePlaybackUpdated(PlaybackUpdatedEvent event) {
        try {
            playbackService.broadcastPlaybackUpdate(event.getPartyId(), event.getQueueItemId(), event.isPlaying());
        } catch (Exception e) {
            // Log but don't fail - transaction is already committed
            log.error("Failed to broadcast playback update for party {}", event.getPartyId(), e);
        }
    }
}
