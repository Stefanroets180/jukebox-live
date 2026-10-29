package com.jukebox.live.service;

import com.jukebox.live.entity.Party;
import com.jukebox.live.entity.QueueItem;
import com.jukebox.live.entity.Track;
import com.jukebox.live.entity.User;
import com.jukebox.live.repository.PartyRepository;
import com.jukebox.live.repository.QueueItemRepository;
import com.jukebox.live.repository.TrackRepository;
import com.jukebox.live.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class PlaybackServiceConcurrencyTest {

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

    @AfterEach
    void cleanup() {
        queueItemRepository.deleteAll();
        trackRepository.deleteAll();
        partyRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    public void testCompleteCurrentTrack_ConcurrentCalls_ShouldAdvanceOnce() throws Exception {
        // Add three pending items
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

        // Use CountDownLatch to synchronize concurrent calls
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);
        AtomicBoolean hasError = new AtomicBoolean(false);

        Runnable task = () -> {
            try {
                startLatch.await();
                playbackService.completeCurrentTrack(partyId, item1.getId());
            } catch (Exception e) {
                hasError.set(true);
                e.printStackTrace();
            } finally {
                doneLatch.countDown();
            }
        };

        Thread thread1 = new Thread(task);
        Thread thread2 = new Thread(task);
        
        thread1.start();
        thread2.start();
        
        startLatch.countDown(); // Release both threads simultaneously
        boolean finished = doneLatch.await(5, TimeUnit.SECONDS);

        assertTrue(finished, "Threads should finish within timeout");
        assertFalse(hasError.get(), "No worker thread should throw exception");

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
}
