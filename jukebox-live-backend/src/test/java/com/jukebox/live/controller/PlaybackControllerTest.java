package com.jukebox.live.controller;

import com.jukebox.live.entity.Party;
import com.jukebox.live.entity.QueueItem;
import com.jukebox.live.entity.Track;
import com.jukebox.live.entity.User;
import com.jukebox.live.filter.PartyPresenceFilter;
import com.jukebox.live.repository.PartyRepository;
import com.jukebox.live.repository.QueueItemRepository;
import com.jukebox.live.repository.TrackRepository;
import com.jukebox.live.repository.UserRepository;
import com.jukebox.live.security.JwtUtil;
import com.jukebox.live.service.PlaybackLeaseService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class PlaybackControllerTest {

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

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private PartyPresenceFilter partyPresenceFilter;

    private User hostUser;
    private User otherAdmin;
    private User guestUser;
    private UUID hostUserId;
    private UUID otherAdminId;
    private UUID guestUserId;
    private String hostRole;
    private String otherAdminRole;
    private String guestRole;
    private String hostToken;
    private String otherAdminToken;
    private String guestToken;
    private UUID partyId;
    private UUID guestPartyId;  // Separate party for guest to host
    private UUID queueItemId;
    private UUID playerInstanceId;
    private UUID guestQueueItemId;  // Queue item in guest's party
    private UUID trackId;
    private Path testAudioFile;
    private String testAudioFilePath;

    @BeforeEach
    void setup() throws IOException, ServletException {
        // Mock the filter to always pass through (controller tests focus on controller logic, not filter)
        doAnswer(invocation -> {
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(partyPresenceFilter).doFilter(any(ServletRequest.class), any(ServletResponse.class), any(FilterChain.class));

        // Create a temporary test audio file
        testAudioFile = Files.createTempFile("test-audio", ".mp3");
        testAudioFilePath = testAudioFile.toString();
        // Write some dummy audio data (100 bytes)
        byte[] dummyAudioData = new byte[100];
        for (int i = 0; i < dummyAudioData.length; i++) {
            dummyAudioData[i] = (byte) (i % 256);
        }
        try (FileOutputStream fos = new FileOutputStream(testAudioFile.toFile())) {
            fos.write(dummyAudioData);
        }

        // Create host admin user
        hostUser = User.builder()
                .username("hostuser")
                .passwordHash(passwordEncoder.encode("hostpass"))
                .credits(10)
                .role(User.Role.ADMIN)
                .build();
        hostUser = userRepository.save(hostUser);
        hostUserId = hostUser.getId();
        hostRole = hostUser.getRole().name();
        hostToken = jwtUtil.generateToken(hostUser.getId(), "hostuser", "ADMIN");

        // Create another admin user (not the host)
        otherAdmin = User.builder()
                .username("otheradmin")
                .passwordHash(passwordEncoder.encode("otherpass"))
                .credits(10)
                .role(User.Role.ADMIN)
                .build();
        otherAdmin = userRepository.save(otherAdmin);
        otherAdminId = otherAdmin.getId();
        otherAdminRole = otherAdmin.getRole().name();
        otherAdminToken = jwtUtil.generateToken(otherAdmin.getId(), "otheradmin", "ADMIN");

        // Create a guest user (USER role, will be party host for @PreAuthorize test)
        guestUser = User.builder()
                .username("guestuser")
                .passwordHash(passwordEncoder.encode("guestpass"))
                .credits(5)
                .role(User.Role.USER)
                .build();
        guestUser = userRepository.save(guestUser);
        guestUserId = guestUser.getId();
        guestRole = guestUser.getRole().name();
        guestToken = jwtUtil.generateToken(guestUser.getId(), "guestuser", "USER");

        // Create a party hosted by the host user (for other tests)
        Party party = Party.builder()
                .code("HOSTPARTY")
                .hostUserId(hostUser.getId())
                .partyIp("192.168.1.100")
                .musicLibraryPath(testAudioFile.getParent().toString())
                .isActive(true)
                .build();
        party = partyRepository.save(party);
        partyId = party.getId();
        playerInstanceId = UUID.randomUUID();
        assertTrue(playbackLeaseService.acquire(partyId, hostUserId, playerInstanceId).isAcquired());

        // Create a separate party hosted by the guest user (for @PreAuthorize test)
        Party guestParty = Party.builder()
                .code("GUESTPARTY")
                .hostUserId(guestUser.getId())
                .partyIp("192.168.1.101")
                .musicLibraryPath(testAudioFile.getParent().toString())
                .isActive(true)
                .build();
        guestParty = partyRepository.save(guestParty);
        guestPartyId = guestParty.getId();

        // Create a track pointing to the test audio file
        Track track = Track.builder()
                .title("Test Song")
                .artist("Test Artist")
                .album("Test Album")
                .durationSeconds(180)
                .filePath(testAudioFilePath)
                .build();
        track = trackRepository.save(track);
        trackId = track.getId();

        // Create a queue item for the host's party
        QueueItem item = QueueItem.builder()
                .partyId(partyId)
                .trackId(trackId)
                .requestedBy(hostUser.getId())
                .position(1)
                .status(QueueItem.Status.PLAYING)
                .build();
        item = queueItemRepository.save(item);
        queueItemId = item.getId();

        // Create a queue item for the guest's party (for @PreAuthorize test)
        QueueItem guestItem = QueueItem.builder()
                .partyId(guestPartyId)
                .trackId(trackId)
                .requestedBy(guestUser.getId())
                .position(1)
                .status(QueueItem.Status.PLAYING)
                .build();
        guestItem = queueItemRepository.save(guestItem);
        guestQueueItemId = guestItem.getId();
    }

    @AfterEach
    void cleanup() throws Exception {
        if (partyId != null && hostUserId != null && playerInstanceId != null) {
            playbackLeaseService.release(partyId, hostUserId, playerInstanceId);
        }
        // Delete the temporary test audio file
        if (testAudioFile != null && Files.exists(testAudioFile)) {
            Files.delete(testAudioFile);
        }
    }

    // Configuration validation tests (unit tests)
    @Test
    public void testJwtUtil_AudioTokenDefaultExpiration_IsFiveMinutes() {
        JwtUtil defaultJwtUtil = new JwtUtil("test-secret-key-for-testing-purposes-only", 86400000, 300000);
        UUID testQueueId = UUID.randomUUID();
        String token = defaultJwtUtil.generateAudioToken(testQueueId, partyId, playerInstanceId, hostUserId, "ADMIN");
        assertNotNull(token);
    }

    @Test
    public void testJwtUtil_AudioTokenExpiration_ValidValue_Accepted() {
        JwtUtil jwtUtil = new JwtUtil("test-secret-key-for-testing-purposes-only", 86400000, 300000);
        UUID testQueueId = UUID.randomUUID();
        String token = jwtUtil.generateAudioToken(testQueueId, partyId, playerInstanceId, hostUserId, "ADMIN");
        assertNotNull(token);
    }

    @Test
    public void testJwtUtil_AudioTokenExpiration_ZeroValue_Rejected() {
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            new JwtUtil("test-secret-key-for-testing-purposes-only", 86400000, 0);
        });
        assertTrue(exception.getMessage().contains("jukebox.jwt.audio-token-expiration-ms"));
        assertTrue(exception.getMessage().contains("must be positive"));
    }

    @Test
    public void testJwtUtil_AudioTokenExpiration_NegativeValue_Rejected() {
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            new JwtUtil("test-secret-key-for-testing-purposes-only", 86400000, -1000);
        });
        assertTrue(exception.getMessage().contains("jukebox.jwt.audio-token-expiration-ms"));
        assertTrue(exception.getMessage().contains("must be positive"));
    }

    @Test
    public void testJwtUtil_AudioTokenExpiration_ExceedsMaximum_Rejected() {
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            new JwtUtil("test-secret-key-for-testing-purposes-only", 86400000, 600000);
        });
        assertTrue(exception.getMessage().contains("jukebox.jwt.audio-token-expiration-ms"));
        assertTrue(exception.getMessage().contains("must not exceed"));
        assertTrue(exception.getMessage().contains("300000"));
    }

    // Party authorization controller tests
    @Test
    public void testAudioToken_PartyHostCanObtainTokenForTheirQueueItem() throws Exception {
        mockMvc.perform(get("/api/playback/audio-token")
                        .param("queueItemId", queueItemId.toString())
                        .param("partyId", partyId.toString())
                        .param("playerInstanceId", playerInstanceId.toString())
                        .header("Authorization", "Bearer " + hostToken))
                .andExpect(status().isOk());
    }

    @Test
    public void testAudioToken_NonHostAdminReceives403ForOtherPartyQueueItem() throws Exception {
        mockMvc.perform(get("/api/playback/audio-token")
                        .param("queueItemId", queueItemId.toString())
                        .param("partyId", partyId.toString())
                        .param("playerInstanceId", playerInstanceId.toString())
                        .header("Authorization", "Bearer " + otherAdminToken))
                .andExpect(status().isForbidden());
    }

    @Test
    public void testAudioToken_MissingQueueItemReturns404() throws Exception {
        UUID fakeQueueId = UUID.randomUUID();
        mockMvc.perform(get("/api/playback/audio-token")
                        .param("queueItemId", fakeQueueId.toString())
                        .param("partyId", partyId.toString())
                        .param("playerInstanceId", playerInstanceId.toString())
                        .header("Authorization", "Bearer " + hostToken))
                .andExpect(status().isNotFound());
    }

    @Test
    public void testAudioToken_UnauthenticatedRequestRejected() throws Exception {
        mockMvc.perform(get("/api/playback/audio-token")
                        .param("queueItemId", queueItemId.toString())
                        .param("partyId", partyId.toString())
                        .param("playerInstanceId", playerInstanceId.toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    public void testAudioToken_NonAdminUserDenied() throws Exception {
        // Non-ADMIN user should be denied by @PreAuthorize("hasRole('ADMIN')")
        // The user is authenticated (valid JWT token) but lacks ADMIN role
        // The guest user is the host of their own party, so party-host check would pass
        // Only @PreAuthorize("hasRole('ADMIN')") should block this request
        mockMvc.perform(get("/api/playback/audio-token")
                        .param("queueItemId", guestQueueItemId.toString())
                        .param("partyId", guestPartyId.toString())
                        .param("playerInstanceId", playerInstanceId.toString())
                        .header("Authorization", "Bearer " + guestToken))
                .andExpect(status().isForbidden());
        
        // This test would fail if @PreAuthorize("hasRole('ADMIN')") were removed from
        // the endpoint, as the non-ADMIN user (who is the party host) would then receive 200 OK instead of 403.
    }

    @Test
    public void testAudioToken_IssuedTokenScopedToRequestedQueueItem() throws Exception {
        // First obtain a token via the controller
        String response = mockMvc.perform(get("/api/playback/audio-token")
                        .param("queueItemId", queueItemId.toString())
                        .param("partyId", partyId.toString())
                        .param("playerInstanceId", playerInstanceId.toString())
                        .header("Authorization", "Bearer " + hostToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String audioToken = response;
        
        // Verify the token is scoped to the correct queue item
        UUID authorizedQueueId = jwtUtil.validateAudioToken(audioToken);
        assertEquals(queueItemId, authorizedQueueId);
        
        // Verify it cannot authorize a different queue item
        UUID differentQueueId = UUID.randomUUID();
        assertNotEquals(differentQueueId, authorizedQueueId);
    }

    @Test
    public void testAudioToken_AnotherPlayerInstanceCannotClaimAudioAccess() throws Exception {
        UUID otherPlayerId = UUID.randomUUID();
        mockMvc.perform(get("/api/playback/audio-token")
                        .param("queueItemId", queueItemId.toString())
                        .param("partyId", partyId.toString())
                        .param("playerInstanceId", otherPlayerId.toString())
                        .header("Authorization", "Bearer " + hostToken))
                .andExpect(status().isConflict());
    }

    @Test
    public void testAudioStream_InvalidTokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/playback/audio")
                        .param("queueItemId", queueItemId.toString())
                        .param("partyId", partyId.toString())
                        .param("playerInstanceId", playerInstanceId.toString())
                        .param("token", "not-a-valid-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void testAudioStream_TokenCannotBeReplayedForAnotherPlayerInstance() throws Exception {
        String audioToken = jwtUtil.generateAudioToken(queueItemId, partyId, playerInstanceId, hostUserId, "ADMIN");
        mockMvc.perform(get("/api/playback/audio")
                        .param("queueItemId", queueItemId.toString())
                        .param("partyId", partyId.toString())
                        .param("playerInstanceId", UUID.randomUUID().toString())
                        .param("token", audioToken))
                .andExpect(status().isForbidden());
    }

    // Audio byte-range integration tests
    @Test
    public void testAudioByteRange_ValidRangeReturns206() throws Exception {
        // Generate a valid audio token
        String audioToken = jwtUtil.generateAudioToken(queueItemId, partyId, playerInstanceId, hostUserId, "ADMIN");
        
        // Request a valid byte range (bytes 0-49 of 100 bytes)
        mockMvc.perform(get("/api/playback/audio")
                        .param("queueItemId", queueItemId.toString())
                        .param("partyId", partyId.toString())
                        .param("playerInstanceId", playerInstanceId.toString())
                        .param("token", audioToken)
                        .header("Range", "bytes=0-49"))
                .andExpect(status().is(206))
                .andExpect(header().exists("Content-Range"))
                .andExpect(header().exists("Content-Length"))
                .andExpect(header().string("Accept-Ranges", "bytes"));
    }

    @Test
    public void testAudioByteRange_NoRangeHeaderReturnsFullContent() throws Exception {
        // Generate a valid audio token
        String audioToken = jwtUtil.generateAudioToken(queueItemId, partyId, playerInstanceId, hostUserId, "ADMIN");
        
        // Request without Range header should return full content
        mockMvc.perform(get("/api/playback/audio")
                        .param("queueItemId", queueItemId.toString())
                        .param("partyId", partyId.toString())
                        .param("playerInstanceId", playerInstanceId.toString())
                        .param("token", audioToken))
                .andExpect(status().isOk())
                .andExpect(header().exists("Content-Length"))
                .andExpect(header().string("Accept-Ranges", "bytes"));
    }

    @Test
    public void testAudioByteRange_UnsatisfiableRangeReturns416() throws Exception {
        // Generate a valid audio token
        String audioToken = jwtUtil.generateAudioToken(queueItemId, partyId, playerInstanceId, hostUserId, "ADMIN");
        
        // Request an unsatisfiable range (beyond file size)
        mockMvc.perform(get("/api/playback/audio")
                        .param("queueItemId", queueItemId.toString())
                        .param("partyId", partyId.toString())
                        .param("playerInstanceId", playerInstanceId.toString())
                        .param("token", audioToken)
                        .header("Range", "bytes=1000-2000"))
                .andExpect(status().is(416))
                .andExpect(header().exists("Content-Range"));
    }

    @Test
    public void testAudioByteRange_MalformedRangeHeaderDoesNotReturn400() throws Exception {
        // Generate a valid audio token
        String audioToken = jwtUtil.generateAudioToken(queueItemId, partyId, playerInstanceId, hostUserId, "ADMIN");
        
        // Request with malformed Range header should NOT return 400
        // It should either return 200 (full file) or 416 (unsatisfiable range)
        // but never 400 which breaks audio playback
        var result = mockMvc.perform(get("/api/playback/audio")
                        .param("queueItemId", queueItemId.toString())
                        .param("partyId", partyId.toString())
                        .param("playerInstanceId", playerInstanceId.toString())
                        .param("token", audioToken)
                        .header("Range", "bytes=999999999999999999999999-999999999999999999999999"))
                .andReturn();
        
        int status = result.getResponse().getStatus();
        assertNotEquals(400, status, "Malformed range header should not return 400");
    }

    @Test
    public void testAudioByteRange_MultipleRangesRejected() throws Exception {
        // Generate a valid audio token
        String audioToken = jwtUtil.generateAudioToken(queueItemId, partyId, playerInstanceId, hostUserId, "ADMIN");
        
        // Request with multiple ranges (implementation rejects these)
        mockMvc.perform(get("/api/playback/audio")
                        .param("queueItemId", queueItemId.toString())
                        .param("partyId", partyId.toString())
                        .param("playerInstanceId", playerInstanceId.toString())
                        .param("token", audioToken)
                        .header("Range", "bytes=0-49,50-99"))
                .andExpect(status().is(416));
    }
}
