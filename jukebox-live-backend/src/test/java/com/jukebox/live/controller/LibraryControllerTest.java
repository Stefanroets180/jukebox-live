package com.jukebox.live.controller;

import com.jukebox.live.entity.ArtworkLookupStatus;
import com.jukebox.live.entity.Track;
import com.jukebox.live.entity.User;
import com.jukebox.live.repository.TrackRepository;
import com.jukebox.live.repository.UserRepository;
import com.jukebox.live.security.JwtUtil;
import com.jukebox.live.service.ArtworkMetadataProvider;
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

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class LibraryControllerTest {

    private static final String RELEASE_ID = "11111111-1111-4111-8111-111111111111";
    private static final String COVER_URL = "https://coverartarchive.org/release/11111111-1111-4111-8111-111111111111/front-500";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TrackRepository trackRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private ArtworkMetadataProvider artworkMetadataProvider;

    private String adminToken;
    private String userToken;
    private Track track;

    @BeforeEach
    void setUp() {
        User admin = userRepository.save(User.builder()
                .username("art-admin-" + UUID.randomUUID())
                .passwordHash(passwordEncoder.encode("password"))
                .credits(0)
                .role(User.Role.ADMIN)
                .build());
        User user = userRepository.save(User.builder()
                .username("art-user-" + UUID.randomUUID())
                .passwordHash(passwordEncoder.encode("password"))
                .credits(0)
                .role(User.Role.USER)
                .build());
        adminToken = jwtUtil.generateToken(admin.getId(), admin.getUsername(), "ADMIN");
        userToken = jwtUtil.generateToken(user.getId(), user.getUsername(), "USER");

        track = trackRepository.save(Track.builder()
                .title("Night Drive")
                .artist("Artist One")
                .album("Night Drive")
                .durationSeconds(210)
                .filePath("/music/night-drive-" + UUID.randomUUID() + ".mp3")
                .musicBrainzReleaseId(RELEASE_ID)
                .artworkLookupStatus(ArtworkLookupStatus.NOT_LOOKED_UP)
                .build());
    }

    @Test
    void adminLookupPersistsArtworkAndLibrarySearchReturnsIt() throws Exception {
        when(artworkMetadataProvider.getFrontCoverUrl(RELEASE_ID)).thenReturn(Optional.of(COVER_URL));

        mockMvc.perform(post("/api/library/artwork/{trackId}/lookup", track.getId())
                        .param("retry", "false")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FOUND"))
                .andExpect(jsonPath("$.albumArtPath").value(COVER_URL));

        mockMvc.perform(get("/api/library/search")
                        .param("q", "Night Drive")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].albumArtPath").value(COVER_URL))
                .andExpect(jsonPath("$[0].musicBrainzReleaseId").value(RELEASE_ID));

        Track saved = trackRepository.findById(track.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(saved.getArtworkLookupStatus()).isEqualTo(ArtworkLookupStatus.FOUND);
        org.assertj.core.api.Assertions.assertThat(saved.getArtworkSourceUrl()).contains(RELEASE_ID);
    }

    @Test
    void nonAdminCannotUseArtworkDiscoveryEndpoints() throws Exception {
        mockMvc.perform(post("/api/library/artwork/{trackId}/lookup", track.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }
}
