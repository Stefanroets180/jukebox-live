package com.jukebox.live.service;

import com.jukebox.live.dto.NowPlayingDto;
import com.jukebox.live.dto.QueueItemDto;
import com.jukebox.live.dto.RequesterDto;
import com.jukebox.live.dto.TrackDto;
import com.jukebox.live.entity.QueueItem;
import com.jukebox.live.entity.Track;
import com.jukebox.live.entity.User;
import com.jukebox.live.event.PlaybackUpdatedEvent;
import com.jukebox.live.repository.QueueItemRepository;
import com.jukebox.live.repository.TrackRepository;
import com.jukebox.live.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlaybackService {

    private final QueueItemRepository queueItemRepository;
    private final TrackRepository trackRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Start the next pending track for a party if the player is idle.
     * Returns true if a track was started, false if no pending tracks exist.
     */
    @Transactional
    public boolean startNextTrack(UUID partyId) {
        return startNextTrack(partyId, false);
    }

    private boolean startNextTrack(UUID partyId, boolean paused) {
        // Check if a track is already playing
        Optional<QueueItem> currentlyPlaying = queueItemRepository
                .findFirstByPartyIdAndStatusOrderByPositionAsc(partyId, QueueItem.Status.PLAYING);
        
        if (currentlyPlaying.isPresent()) {
            log.debug("Party {} already has a track playing: {}", partyId, currentlyPlaying.get().getId());
            return false;
        }

        // Find the next pending track
        List<QueueItem> pendingTracks = queueItemRepository
                .findByPartyIdAndStatusOrderByPositionAsc(partyId, QueueItem.Status.PENDING);

        if (pendingTracks.isEmpty()) {
            log.debug("No pending tracks for party {}", partyId);
            eventPublisher.publishEvent(new PlaybackUpdatedEvent(this, partyId, null, false));
            return false;
        }

        // Start the first pending track
        QueueItem nextTrack = pendingTracks.get(0);
        nextTrack.setStatus(QueueItem.Status.PLAYING);
        nextTrack.setProgressSeconds(0);
        nextTrack.setIsPaused(paused);
        queueItemRepository.save(nextTrack);

        log.info("Started playback for party {}: track {} at position {}", 
                partyId, nextTrack.getTrackId(), nextTrack.getPosition());

        // Publish playback update event
        eventPublisher.publishEvent(new PlaybackUpdatedEvent(this, partyId, nextTrack.getId(), !paused));

        return true;
    }

    /**
     * Mark the current track as completed and start the next one.
     */
    @Transactional
    public void completeCurrentTrack(UUID partyId, UUID queueItemId) {
        // Use conditional UPDATE to ensure only one request can transition PLAYING -> COMPLETED
        // This provides idempotency for the specific queue item
        int updated = queueItemRepository.completeQueueItem(queueItemId, partyId);

        if (updated == 0) {
            log.debug("Queue item {} was already completed or skipped", queueItemId);
            return;
        }

        log.info("Completed track for party {}: queue item {}", partyId, queueItemId);

        // Start the next track only if our update succeeded
        startNextTrack(partyId);
    }

    /**
     * Skip the current track and start the next one.
     */
    @Transactional
    public void skipCurrentTrack(UUID partyId) {
        Optional<QueueItem> currentlyPlaying = queueItemRepository
                .findFirstByPartyIdAndStatusOrderByPositionAsc(partyId, QueueItem.Status.PLAYING);

        if (currentlyPlaying.isEmpty()) {
            log.debug("No track currently playing for party {}", partyId);
            return;
        }

        QueueItem skippedTrack = currentlyPlaying.get();
        boolean wasPaused = Boolean.TRUE.equals(skippedTrack.getIsPaused());
        skippedTrack.setStatus(QueueItem.Status.SKIPPED);
        queueItemRepository.save(skippedTrack);

        log.info("Skipped track for party {}: track {}", partyId, skippedTrack.getTrackId());

        // Start the next track without changing the room's pause state.
        startNextTrack(partyId, wasPaused);
    }

    /**
     * Get the currently playing track for a party.
     */
    public Optional<QueueItem> getNowPlaying(UUID partyId) {
        return queueItemRepository
                .findFirstByPartyIdAndStatusOrderByPositionAsc(partyId, QueueItem.Status.PLAYING);
    }

    @Transactional
    public void updateProgress(UUID partyId, UUID queueItemId, Integer progressSeconds) {
        Optional<QueueItem> currentlyPlaying = getNowPlaying(partyId);
        if (currentlyPlaying.isEmpty() || !currentlyPlaying.get().getId().equals(queueItemId)) {
            return;
        }

        QueueItem item = currentlyPlaying.get();
        int safeProgress = Math.max(0, progressSeconds == null ? 0 : progressSeconds);
        Track track = trackRepository.findById(item.getTrackId()).orElse(null);
        if (track != null && track.getDurationSeconds() != null && track.getDurationSeconds() > 0) {
            safeProgress = Math.min(safeProgress, track.getDurationSeconds());
        }

        int updated = queueItemRepository.updateProgressForQueueItem(
                queueItemId, partyId, QueueItem.Status.PLAYING, safeProgress);
        if (updated > 0) {
            eventPublisher.publishEvent(new PlaybackUpdatedEvent(
                    this, partyId, item.getId(), !Boolean.TRUE.equals(item.getIsPaused())));
        }
    }

    @Transactional
    public void setPaused(UUID partyId, boolean paused) {
        getNowPlaying(partyId).ifPresent(item -> {
            item.setIsPaused(paused);
            queueItemRepository.save(item);
            eventPublisher.publishEvent(new PlaybackUpdatedEvent(this, partyId, item.getId(), !paused));
        });
    }

    /**
     * Get the track file path for a queue item.
     */
    public Optional<String> getTrackFilePath(UUID queueItemId) {
        Optional<QueueItem> queueItem = queueItemRepository.findById(queueItemId);
        if (queueItem.isEmpty()) {
            return Optional.empty();
        }

        Optional<Track> track = trackRepository.findById(queueItem.get().getTrackId());
        return track.map(Track::getFilePath);
    }

    /**
     * Broadcast playback state update via WebSocket.
     */
    public void broadcastPlaybackUpdate(UUID partyId, UUID queueItemId, boolean isPlaying) {
        NowPlayingDto nowPlaying = null;

        if (queueItemId != null) {
            Optional<QueueItem> queueItem = queueItemRepository.findById(queueItemId);
            if (queueItem.isPresent() && queueItem.get().getStatus() == QueueItem.Status.PLAYING) {
                Optional<Track> track = trackRepository.findById(queueItem.get().getTrackId());
                Optional<User> requester = userRepository.findById(queueItem.get().getRequestedBy());

                if (track.isPresent() && requester.isPresent()) {
                    Track currentTrack = track.get();
                    QueueItem currentItem = queueItem.get();
                    nowPlaying = NowPlayingDto.builder()
                            .track(TrackDto.builder()
                                    .id(currentTrack.getId().toString())
                                    .title(currentTrack.getTitle())
                                    .artist(currentTrack.getArtist())
                                    .album(currentTrack.getAlbum())
                                    .durationSeconds(currentTrack.getDurationSeconds())
                                    .albumArtPath(currentTrack.getAlbumArtPath())
                                    .genre(currentTrack.getGenre())
                                    .musicBrainzReleaseId(currentTrack.getMusicBrainzReleaseId())
                                    .artworkLookupStatus(currentTrack.getArtworkLookupStatus().name())
                                    .artworkSourceUrl(currentTrack.getArtworkSourceUrl())
                                    .build())
                            .progressSeconds(currentItem.getProgressSeconds())
                            .isPlaying(isPlaying && !Boolean.TRUE.equals(currentItem.getIsPaused()))
                            .requestedBy(RequesterDto.builder().username(requester.get().getUsername()).build())
                            .build();
                }
            }
        }

        messagingTemplate.convertAndSend("/topic/playback/" + partyId, nowPlaying);
    }
}
