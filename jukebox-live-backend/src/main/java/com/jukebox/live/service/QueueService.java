package com.jukebox.live.service;

import com.jukebox.live.dto.*;
import com.jukebox.live.entity.QueueItem;
import com.jukebox.live.entity.Track;
import com.jukebox.live.entity.User;
import com.jukebox.live.event.CreditUpdatedEvent;
import com.jukebox.live.event.QueueUpdatedEvent;
import com.jukebox.live.repository.QueueItemRepository;
import com.jukebox.live.repository.TrackRepository;
import com.jukebox.live.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class QueueService {

    private final QueueItemRepository queueItemRepository;
    private final TrackRepository trackRepository;
    private final UserRepository userRepository;
    private final CreditService creditService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ApplicationEventPublisher eventPublisher;
    private final PlaybackService playbackService;

    public List<QueueItemDto> getQueue(UUID partyId) {
        return queueItemRepository.findByPartyIdOrderByPositionAsc(partyId)
                .stream()
                .filter(item -> item.getStatus() == QueueItem.Status.PENDING || item.getStatus() == QueueItem.Status.PLAYING)
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public AddToQueueResponse addToQueue(UUID partyId, UUID userId, String trackId, String clientIp) {
        Track track = trackRepository.findById(UUID.fromString(trackId))
                .orElseThrow(() -> new RuntimeException("Track not found"));

        // Deduct credit first - this will publish CreditUpdatedEvent after commit
        // If this fails (insufficient credits), the entire transaction rolls back
        creditService.spendCredit(userId, partyId, "Queued: " + track.getTitle());
        
        // Reload user to get the actual credit balance after deduction
        User updatedUser = userRepository.findById(userId).orElseThrow();

        // Allocate next position atomically using database-level locking
        Integer nextPosition = queueItemRepository.allocateNextPosition(partyId);

        QueueItem item = QueueItem.builder()
                .partyId(partyId)
                .trackId(track.getId())
                .requestedBy(userId)
                .position(nextPosition)
                .cost(1)
                .status(QueueItem.Status.PENDING)
                .build();
        queueItemRepository.save(item);

        // Start playback if player is idle
        playbackService.startNextTrack(partyId);

        // Publish events to be broadcast after transaction commits
        eventPublisher.publishEvent(new QueueUpdatedEvent(this, partyId));
        eventPublisher.publishEvent(new CreditUpdatedEvent(this, userId, updatedUser.getCredits()));

        return AddToQueueResponse.builder()
                .id(item.getId().toString())
                .trackId(trackId)
                .status(item.getStatus().name())
                .position(item.getPosition())
                .cost(item.getCost())
                .build();
    }

    @Transactional
    public void skipQueueItem(UUID queueId, UUID adminUserId) {
        QueueItem item = queueItemRepository.findById(queueId)
                .orElseThrow(() -> new RuntimeException("Queue item not found"));
        item.setStatus(QueueItem.Status.SKIPPED);
        queueItemRepository.save(item);
        eventPublisher.publishEvent(new QueueUpdatedEvent(this, item.getPartyId()));
    }

    private QueueItemDto mapToDto(QueueItem item) {
        Track track = trackRepository.findById(item.getTrackId()).orElse(null);
        User requester = userRepository.findById(item.getRequestedBy()).orElse(null);
        
        // Handle missing track or user gracefully
        String trackTitle = track != null ? track.getTitle() : "Unknown Track";
        String trackArtist = track != null ? track.getArtist() : "Unknown Artist";
        String trackAlbum = track != null ? track.getAlbum() : null;
        Integer duration = track != null ? track.getDurationSeconds() : null;
        String albumArt = track != null ? track.getAlbumArtPath() : null;
        String genre = track != null ? track.getGenre() : null;
        String requesterUsername = requester != null ? requester.getUsername() : "Unknown User";
        
        return QueueItemDto.builder()
                .id(item.getId().toString())
                .track(TrackDto.builder()
                        .id(item.getTrackId().toString())
                        .title(trackTitle)
                        .artist(trackArtist)
                        .album(trackAlbum)
                        .durationSeconds(duration)
                        .albumArtPath(albumArt)
                        .genre(genre)
                        .build())
                .requestedBy(RequesterDto.builder().username(requesterUsername).build())
                .status(item.getStatus().name())
                .position(item.getPosition())
                .createdAt(item.getCreatedAt().toString())
                .build();
    }

    public void broadcastQueueUpdate(UUID partyId) {
        messagingTemplate.convertAndSend("/topic/queue/" + partyId, getQueue(partyId));
    }

    public void broadcastCreditUpdate(UUID userId, Integer credits) {
        messagingTemplate.convertAndSend("/topic/credits/" + userId, credits);
    }
}
