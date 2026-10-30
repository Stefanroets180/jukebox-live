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

    @Test
    void searchReturnsMatchingTracks() throws Exception {
        mockMvc.perform(get("/api/library/search")
                        .param("q", "Night")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Night Drive"))
                .andExpect(jsonPath("$[0].artist").value("Artist One"));
    }

    @Test
    void searchReturnsEmptyForNonMatchingQuery() throws Exception {
        mockMvc.perform(get("/api/library/search")
                        .param("q", "NonExistentSong")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void artistsReturnsAlphabeticalList() throws Exception {
        // Create additional tracks with artists in non-alphabetical order
        trackRepository.save(Track.builder()
                .title("Song Z")
                .artist("Zebra Artist")
                .album("Album Z")
                .durationSeconds(180)
                .filePath("/music/song-z.mp3")
                .build());
        trackRepository.save(Track.builder()
                .title("Song A")
                .artist("Alpha Artist")
                .album("Album A")
                .durationSeconds(200)
                .filePath("/music/song-a.mp3")
                .build());
        trackRepository.save(Track.builder()
                .title("Song M")
                .artist("Middle Artist")
                .album("Album M")
                .durationSeconds(190)
                .filePath("/music/song-m.mp3")
                .build());

        mockMvc.perform(get("/api/library/artists")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0]").value("Alpha Artist"))
                .andExpect(jsonPath("$[1]").value("Artist One"))
                .andExpect(jsonPath("$[2]").value("Middle Artist"))
                .andExpect(jsonPath("$[3]").value("Zebra Artist"));
    }

    @Test
    void artistTracksReturnsTracksForArtist() throws Exception {
        mockMvc.perform(get("/api/library/artists/{artist}/tracks", "Artist One")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].artist").value("Artist One"));
    }

    @Test
    void albumTracksReturnsTracksForAlbum() throws Exception {
        mockMvc.perform(get("/api/library/artists/{artist}/albums/{album}/tracks", "Artist One", "Night Drive")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].artist").value("Artist One"))
                .andExpect(jsonPath("$[0].album").value("Night Drive"));
    }

    @Test
    void genreTracksReturnsTracksForGenre() throws Exception {
        track.setGenre("Electronic");
        trackRepository.save(track);

        mockMvc.perform(get("/api/library/genres/{genre}/tracks", "Electronic")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].genre").value("Electronic"));
    }

    @Test
    void genreTracksFiltersCorrectlyWithMultipleGenres() throws Exception {
        // Create tracks with different genres
        Track rockTrack1 = trackRepository.save(Track.builder()
                .title("Rock Song 1")
                .artist("Rock Artist")
                .album("Rock Album")
                .durationSeconds(180)
                .filePath("/music/rock1.mp3")
                .genre("Rock")
                .build());
        Track rockTrack2 = trackRepository.save(Track.builder()
                .title("Rock Song 2")
                .artist("Rock Artist")
                .album("Rock Album 2")
                .durationSeconds(190)
                .filePath("/music/rock2.mp3")
                .genre("Rock")
                .build());
        Track jazzTrack = trackRepository.save(Track.builder()
                .title("Jazz Song")
                .artist("Jazz Artist")
                .album("Jazz Album")
                .durationSeconds(200)
                .filePath("/music/jazz.mp3")
                .genre("Jazz")
                .build());
        Track bluesTrack = trackRepository.save(Track.builder()
                .title("Blues Song")
                .artist("Blues Artist")
                .album("Blues Album")
                .durationSeconds(210)
                .filePath("/music/blues.mp3")
                .genre("Blues")
                .build());

        mockMvc.perform(get("/api/library/genres/{genre}/tracks", "Rock")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.id == '" + rockTrack1.getId() + "')]").exists())
                .andExpect(jsonPath("$[?(@.id == '" + rockTrack2.getId() + "')]").exists())
                .andExpect(jsonPath("$[?(@.id == '" + jazzTrack.getId() + "')]").doesNotExist())
                .andExpect(jsonPath("$[?(@.id == '" + bluesTrack.getId() + "')]").doesNotExist());
    }

    @Test
    void genresReturnsDistinctGenres() throws Exception {
        track.setGenre("Rock");
        trackRepository.save(track);
        trackRepository.save(Track.builder()
                .title("Another Song")
                .artist("Artist Two")
                .album("Album Two")
                .durationSeconds(190)
                .filePath("/music/another.mp3")
                .genre("Rock")
                .build());
        trackRepository.save(Track.builder()
                .title("Jazz Song")
                .artist("Jazz Artist")
                .album("Jazz Album")
                .durationSeconds(200)
                .filePath("/music/jazz.mp3")
                .genre("Jazz")
                .build());
        trackRepository.save(Track.builder()
                .title("Blues Song")
                .artist("Blues Artist")
                .album("Blues Album")
                .durationSeconds(180)
                .filePath("/music/blues.mp3")
                .genre("Blues")
                .build());

        mockMvc.perform(get("/api/library/genres")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0]").value("Blues"))
                .andExpect(jsonPath("$[1]").value("Jazz"))
                .andExpect(jsonPath("$[2]").value("Rock"));
    }
}
