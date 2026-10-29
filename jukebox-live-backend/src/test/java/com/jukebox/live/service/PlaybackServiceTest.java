package com.jukebox.live.service;

import com.jukebox.live.entity.Party;
import com.jukebox.live.entity.QueueItem;
import com.jukebox.live.entity.Track;
import com.jukebox.live.entity.User;
import com.jukebox.live.repository.PartyRepository;
import com.jukebox.live.repository.QueueItemRepository;
import com.jukebox.live.repository.TrackRepository;
import com.jukebox.live.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class PlaybackServiceTest {

    @Autowired
    private PlaybackService playbackService;

    @Autowired
    private QueueItemRepository queueItemRepository;

    @Autowired
    private TrackRepository trackRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PartyRepository partyRepository;

    private UUID partyId;
    private UUID trackId;
    private UUID userId;

    @BeforeEach
    void setup() {
        // Create a user
        User user = User.builder()
                .username("testuser")
                .passwordHash("hashed")
                .credits(10)
                .role(User.Role.USER)
                .build();
        user = userRepository.save(user);
        userId = user.getId();

        // Create a party
        Party party = Party.builder()
                .code("TEST01")
                .hostUserId(userId)
                .partyIp("192.168.1.100")
                .musicLibraryPath("/music")
                .isActive(true)
                .build();
        party = partyRepository.save(party);
        partyId = party.getId();

        // Create a track
        Track track = Track.builder()
                .title("Test Song")
                .artist("Test Artist")
                .album("Test Album")
                .durationSeconds(180)
                .filePath("/music/test.mp3")
                .build();
        track = trackRepository.save(track);
        trackId = track.getId();
    }

    @Test
    public void testStartNextTrack_WhenIdle_ShouldStartFirstPending() {
        // Add a pending queue item
        QueueItem item = QueueItem.builder()
                .partyId(partyId)
                .trackId(trackId)
                .requestedBy(userId)
                .position(1)
                .status(QueueItem.Status.PENDING)
                .build();
        queueItemRepository.save(item);

        // Start playback
        boolean started = playbackService.startNextTrack(partyId);

        assertTrue(started, "Should return true when track is started");

        // Verify the item is now playing
        QueueItem playing = queueItemRepository
                .findFirstByPartyIdAndStatusOrderByPositionAsc(partyId, QueueItem.Status.PLAYING)
                .orElse(null);
        assertNotNull(playing, "Should have a playing track");
        assertEquals(item.getId(), playing.getId(), "Should be the same item");
        assertEquals(QueueItem.Status.PLAYING, playing.getStatus(), "Status should be PLAYING");
    }

    @Test
    public void testStartNextTrack_WhenAlreadyPlaying_ShouldNotStart() {
        // Add a playing item
        QueueItem playingItem = QueueItem.builder()
                .partyId(partyId)
                .trackId(trackId)
                .requestedBy(userId)
                .position(1)
                .status(QueueItem.Status.PLAYING)
                .build();
        queueItemRepository.save(playingItem);

        // Add a pending item
        QueueItem pendingItem = QueueItem.builder()
                .partyId(partyId)
                .trackId(trackId)
                .requestedBy(userId)
                .position(2)
                .status(QueueItem.Status.PENDING)
                .build();
        queueItemRepository.save(pendingItem);

        // Try to start playback
        boolean started = playbackService.startNextTrack(partyId);

        assertFalse(started, "Should return false when already playing");

        // Verify the pending item is still pending
        QueueItem pending = queueItemRepository.findById(pendingItem.getId()).orElse(null);
        assertNotNull(pending);
        assertEquals(QueueItem.Status.PENDING, pending.getStatus(), "Pending item should remain pending");
    }

    @Test
    public void testStartNextTrack_WhenNoPending_ShouldReturnFalse() {
        boolean started = playbackService.startNextTrack(partyId);
        assertFalse(started, "Should return false when no pending tracks");
    }

    @Test
    public void testCompleteCurrentTrack_ShouldAdvanceToNext() {
        // Add two pending items
        QueueItem item1 = QueueItem.builder()
                .partyId(partyId)
                .trackId(trackId)
                .requestedBy(userId)
                .position(1)
                .status(QueueItem.Status.PLAYING)
                .build();
        queueItemRepository.save(item1);

        QueueItem item2 = QueueItem.builder()
                .partyId(partyId)
                .trackId(trackId)
                .requestedBy(userId)
                .position(2)
                .status(QueueItem.Status.PENDING)
                .build();
        queueItemRepository.save(item2);

        // Complete current track
        playbackService.completeCurrentTrack(partyId, item1.getId());

        // Verify first item is completed
        QueueItem completed = queueItemRepository.findById(item1.getId()).orElse(null);
        assertNotNull(completed);
        assertEquals(QueueItem.Status.COMPLETED, completed.getStatus());

        // Verify second item is now playing
        QueueItem playing = queueItemRepository
                .findFirstByPartyIdAndStatusOrderByPositionAsc(partyId, QueueItem.Status.PLAYING)
                .orElse(null);
        assertNotNull(playing, "Should have a playing track");
        assertEquals(item2.getId(), playing.getId());
    }

    @Test
    public void testSkipCurrentTrack_ShouldAdvanceToNext() {
        // Add two pending items
        QueueItem item1 = QueueItem.builder()
                .partyId(partyId)
                .trackId(trackId)
                .requestedBy(userId)
                .position(1)
                .status(QueueItem.Status.PLAYING)
                .build();
        queueItemRepository.save(item1);

        QueueItem item2 = QueueItem.builder()
                .partyId(partyId)
                .trackId(trackId)
                .requestedBy(userId)
                .position(2)
                .status(QueueItem.Status.PENDING)
                .build();
        queueItemRepository.save(item2);

        // Skip current track
        playbackService.skipCurrentTrack(partyId);

        // Verify first item is skipped
        QueueItem skipped = queueItemRepository.findById(item1.getId()).orElse(null);
        assertNotNull(skipped);
        assertEquals(QueueItem.Status.SKIPPED, skipped.getStatus());

        // Verify second item is now playing
        QueueItem playing = queueItemRepository
                .findFirstByPartyIdAndStatusOrderByPositionAsc(partyId, QueueItem.Status.PLAYING)
                .orElse(null);
        assertNotNull(playing, "Should have a playing track");
        assertEquals(item2.getId(), playing.getId());
    }

    @Test
    public void testSkipCurrentTrack_WhenPaused_KeepsNextTrackPaused() {
        QueueItem current = QueueItem.builder()
                .partyId(partyId)
                .trackId(trackId)
                .requestedBy(userId)
                .position(1)
                .status(QueueItem.Status.PLAYING)
                .isPaused(true)
                .build();
        queueItemRepository.save(current);

        QueueItem next = QueueItem.builder()
                .partyId(partyId)
                .trackId(trackId)
                .requestedBy(userId)
                .position(2)
                .status(QueueItem.Status.PENDING)
                .build();
        queueItemRepository.save(next);

        playbackService.skipCurrentTrack(partyId);

        assertEquals(QueueItem.Status.SKIPPED, queueItemRepository.findById(current.getId()).orElseThrow().getStatus());
        QueueItem playing = queueItemRepository.findById(next.getId()).orElseThrow();
        assertEquals(QueueItem.Status.PLAYING, playing.getStatus());
        assertTrue(Boolean.TRUE.equals(playing.getIsPaused()));
    }

    @Test
    public void testPauseResumeAndProgressArePersistedAndBounded() {
        QueueItem item = QueueItem.builder()
                .partyId(partyId)
                .trackId(trackId)
                .requestedBy(userId)
                .position(1)
                .status(QueueItem.Status.PLAYING)
                .build();
        queueItemRepository.save(item);

        playbackService.updateProgress(partyId, item.getId(), 250);
        assertEquals(180, queueItemRepository.findById(item.getId()).orElseThrow().getProgressSeconds());

        playbackService.setPaused(partyId, true);
        assertTrue(Boolean.TRUE.equals(queueItemRepository.findById(item.getId()).orElseThrow().getIsPaused()));

        playbackService.updateProgress(partyId, item.getId(), -10);
        assertEquals(0, queueItemRepository.findById(item.getId()).orElseThrow().getProgressSeconds());

        playbackService.setPaused(partyId, false);
        assertFalse(Boolean.TRUE.equals(queueItemRepository.findById(item.getId()).orElseThrow().getIsPaused()));
    }

    @Test
    public void testStaleProgressDoesNotUpdateTheNextQueueItem() {
        QueueItem completed = QueueItem.builder()
                .partyId(partyId)
                .trackId(trackId)
                .requestedBy(userId)
                .position(1)
                .status(QueueItem.Status.COMPLETED)
                .build();
        queueItemRepository.save(completed);

        QueueItem current = QueueItem.builder()
                .partyId(partyId)
                .trackId(trackId)
                .requestedBy(userId)
                .position(2)
                .status(QueueItem.Status.PLAYING)
                .build();
        queueItemRepository.save(current);

        playbackService.updateProgress(partyId, completed.getId(), 75);

        assertEquals(0, queueItemRepository.findById(current.getId()).orElseThrow().getProgressSeconds());
    }

    @Test
    public void testGetNowPlaying_ShouldReturnPlayingItem() {
        QueueItem item = QueueItem.builder()
                .partyId(partyId)
                .trackId(trackId)
                .requestedBy(userId)
                .position(1)
                .status(QueueItem.Status.PLAYING)
                .build();
        queueItemRepository.save(item);

        var nowPlaying = playbackService.getNowPlaying(partyId);
        assertTrue(nowPlaying.isPresent(), "Should return the playing item");
        assertEquals(item.getId(), nowPlaying.get().getId());
    }

    @Test
    public void testGetNowPlaying_WhenNonePlaying_ShouldReturnEmpty() {
        var nowPlaying = playbackService.getNowPlaying(partyId);
        assertFalse(nowPlaying.isPresent(), "Should return empty when no track playing");
    }

    @Test
    public void testCompleteCurrentTrack_WhenQueueEmpty_ShouldStopPlayback() {
        // Add one playing item
        QueueItem item = QueueItem.builder()
                .partyId(partyId)
                .trackId(trackId)
                .requestedBy(userId)
                .position(1)
                .status(QueueItem.Status.PLAYING)
                .build();
        queueItemRepository.save(item);

        // Complete current track
        playbackService.completeCurrentTrack(partyId, item.getId());

        // Verify item is completed
        QueueItem completed = queueItemRepository.findById(item.getId()).orElse(null);
        assertNotNull(completed);
        assertEquals(QueueItem.Status.COMPLETED, completed.getStatus());

        // Verify no track is playing
        var nowPlaying = playbackService.getNowPlaying(partyId);
        assertFalse(nowPlaying.isPresent(), "Should have no playing track after completing last item");
    }

    @Test
    public void testSkipCurrentTrack_WhenQueueEmpty_ShouldStopPlayback() {
        // Add one playing item
        QueueItem item = QueueItem.builder()
                .partyId(partyId)
                .trackId(trackId)
                .requestedBy(userId)
                .position(1)
                .status(QueueItem.Status.PLAYING)
                .build();
        queueItemRepository.save(item);

        // Skip current track
        playbackService.skipCurrentTrack(partyId);

        // Verify item is skipped
        QueueItem skipped = queueItemRepository.findById(item.getId()).orElse(null);
        assertNotNull(skipped);
        assertEquals(QueueItem.Status.SKIPPED, skipped.getStatus());

        // Verify no track is playing
        var nowPlaying = playbackService.getNowPlaying(partyId);
        assertFalse(nowPlaying.isPresent(), "Should have no playing track after skipping last item");
    }

    @Test
    public void testCompleteCurrentTrack_DuplicateCall_ShouldNotAdvanceTwice() {
        // Add two pending items
        QueueItem item1 = QueueItem.builder()
                .partyId(partyId)
                .trackId(trackId)
                .requestedBy(userId)
                .position(1)
                .status(QueueItem.Status.PLAYING)
                .build();
        queueItemRepository.save(item1);

        QueueItem item2 = QueueItem.builder()
                .partyId(partyId)
                .trackId(trackId)
                .requestedBy(userId)
                .position(2)
                .status(QueueItem.Status.PENDING)
                .build();
        queueItemRepository.save(item2);

        QueueItem item3 = QueueItem.builder()
                .partyId(partyId)
                .trackId(trackId)
                .requestedBy(userId)
                .position(3)
                .status(QueueItem.Status.PENDING)
                .build();
        queueItemRepository.save(item3);

        // Complete current track twice with same queueItemId (simulating duplicate frontend calls)
        playbackService.completeCurrentTrack(partyId, item1.getId());
        playbackService.completeCurrentTrack(partyId, item1.getId());

        // Verify first item is completed
        QueueItem completed = queueItemRepository.findById(item1.getId()).orElse(null);
        assertNotNull(completed);
        assertEquals(QueueItem.Status.COMPLETED, completed.getStatus());

        // Verify second item is playing (not third)
        QueueItem playing = queueItemRepository
                .findFirstByPartyIdAndStatusOrderByPositionAsc(partyId, QueueItem.Status.PLAYING)
                .orElse(null);
        assertNotNull(playing, "Should have a playing track");
        assertEquals(item2.getId(), playing.getId(), "Should advance only once to second item, not third");

        // Verify third item is still pending
        QueueItem pending = queueItemRepository.findById(item3.getId()).orElse(null);
        assertNotNull(pending);
        assertEquals(QueueItem.Status.PENDING, pending.getStatus());
    }

    @Test
    public void testCompleteCurrentTrack_AfterSkip_ShouldNotAdvance() {
        QueueItem item1 = QueueItem.builder()
                .partyId(partyId)
                .trackId(trackId)
                .requestedBy(userId)
                .position(1)
                .status(QueueItem.Status.PLAYING)
                .build();
        queueItemRepository.save(item1);

        QueueItem item2 = QueueItem.builder()
                .partyId(partyId)
                .trackId(trackId)
                .requestedBy(userId)
                .position(2)
                .status(QueueItem.Status.PENDING)
                .build();
        queueItemRepository.save(item2);

        // Skip the track
        playbackService.skipCurrentTrack(partyId);

        // Try to complete the already-skipped track
        playbackService.completeCurrentTrack(partyId, item1.getId());

        // Verify item1 is SKIPPED (not COMPLETED)
        QueueItem skipped = queueItemRepository.findById(item1.getId()).orElse(null);
        assertNotNull(skipped);
        assertEquals(QueueItem.Status.SKIPPED, skipped.getStatus());

        // Verify item2 is still playing (not advanced again)
        QueueItem playing = queueItemRepository
                .findFirstByPartyIdAndStatusOrderByPositionAsc(partyId, QueueItem.Status.PLAYING)
                .orElse(null);
        assertNotNull(playing);
        assertEquals(item2.getId(), playing.getId());
    }

    // Note: Concurrent test requires non-transactional test setup to properly verify
    // database-level isolation. The sequential duplicate test above verifies idempotency.
    // Concurrent test moved to separate non-transactional test class.
}
