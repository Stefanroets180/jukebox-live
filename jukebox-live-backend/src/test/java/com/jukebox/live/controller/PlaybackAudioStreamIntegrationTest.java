package com.jukebox.live.controller;

import com.jukebox.live.entity.Party;
import com.jukebox.live.entity.QueueItem;
import com.jukebox.live.entity.Track;
import com.jukebox.live.entity.User;
import com.jukebox.live.repository.PartyRepository;
import com.jukebox.live.repository.QueueItemRepository;
import com.jukebox.live.repository.TrackRepository;
import com.jukebox.live.repository.UserRepository;
import com.jukebox.live.security.JwtUtil;
import com.jukebox.live.service.PlaybackLeaseService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PlaybackAudioStreamIntegrationTest {

    private static final byte[] AUDIO_BYTES = new byte[] { 0, 1, 2, 3, 4, 5, 6, 7 };

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PartyRepository partyRepository;

    @Autowired
    private QueueItemRepository queueItemRepository;

    @Autowired
    private TrackRepository trackRepository;

    @Autowired
    private PlaybackLeaseService playbackLeaseService;

    @TempDir
    Path tempDir;

    private UUID partyId;
    private UUID playerInstanceId;
    private UUID hostUserId;
    private UUID queueItemId;
    private String audioToken;

    @BeforeEach
    void setUp() throws Exception {
        Path audioFile = tempDir.resolve("stream-fixture.mp3");
        Files.write(audioFile, AUDIO_BYTES);

        User host = userRepository.save(User.builder()
                .username("audio-host-" + UUID.randomUUID())
                .passwordHash("test-hash")
                .credits(0)
                .role(User.Role.ADMIN)
                .build());
        hostUserId = host.getId();

        Party party = partyRepository.save(Party.builder()
                .code(UUID.randomUUID().toString().substring(0, 8))
                .hostUserId(hostUserId)
                .partyIp("192.168.1.100")
                .musicLibraryPath(tempDir.toString())
                .isActive(true)
                .build());
        partyId = party.getId();

        Track track = trackRepository.save(Track.builder()
                .title("Stream fixture")
                .artist("Test fixture")
                .album("Test album")
                .durationSeconds(1)
                .filePath(audioFile.toString())
                .build());

        QueueItem item = queueItemRepository.save(QueueItem.builder()
                .partyId(partyId)
                .trackId(track.getId())
                .requestedBy(hostUserId)
                .position(1)
                .status(QueueItem.Status.PLAYING)
                .build());
        queueItemId = item.getId();
        playerInstanceId = UUID.randomUUID();
        playbackLeaseService.acquire(partyId, hostUserId, playerInstanceId);
        audioToken = jwtUtil.generateAudioToken(queueItemId, partyId, playerInstanceId, hostUserId, "ADMIN");
    }

    @AfterEach
    void releaseLease() {
        if (partyId != null && hostUserId != null && playerInstanceId != null) {
            playbackLeaseService.release(partyId, hostUserId, playerInstanceId);
        }
    }

    @Test
    void browserStyleAudioRequestStreamsOnLanWithoutCustomPartyHeader() throws Exception {
        mockMvc.perform(get("/api/playback/audio")
                        .param("queueItemId", queueItemId.toString())
                        .param("partyId", partyId.toString())
                        .param("playerInstanceId", playerInstanceId.toString())
                        .param("token", audioToken)
                        .header(HttpHeaders.RANGE, "bytes=2-5")
                        .with(remoteAddress("192.168.1.45")))
                .andExpect(status().isPartialContent())
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 2-5/8"))
                .andExpect(content().bytes(new byte[] { 2, 3, 4, 5 }));
    }

    @Test
    void validTokenStillCannotStreamFromOutsidePartySubnet() throws Exception {
        mockMvc.perform(get("/api/playback/audio")
                        .param("queueItemId", queueItemId.toString())
                        .param("partyId", partyId.toString())
                        .param("playerInstanceId", playerInstanceId.toString())
                        .param("token", audioToken)
                        .with(remoteAddress("10.0.0.45")))
                .andExpect(status().isForbidden());
    }

    private static RequestPostProcessor remoteAddress(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }
}
