package com.jukebox.live.controller;

import com.jukebox.live.entity.Party;
import com.jukebox.live.entity.PartyGuest;
import com.jukebox.live.entity.Track;
import com.jukebox.live.entity.User;
import com.jukebox.live.filter.PartyPresenceFilter;
import com.jukebox.live.repository.PartyGuestRepository;
import com.jukebox.live.repository.PartyRepository;
import com.jukebox.live.repository.TrackRepository;
import com.jukebox.live.repository.UserRepository;
import com.jukebox.live.security.JwtUtil;
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
import java.io.IOException;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class QueueControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PartyRepository partyRepository;

    @Autowired
    private PartyGuestRepository partyGuestRepository;

    @Autowired
    private TrackRepository trackRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private PartyPresenceFilter partyPresenceFilter;

    private User guest;
    private String guestToken;
    private User admin;
    private String adminToken;
    private UUID partyId;
    private UUID trackId;
    private static final String GUEST_IP = "192.168.1.101";

    @BeforeEach
    void setup() throws IOException, ServletException {
        // Mock the filter to always pass through (controller tests focus on controller logic, not filter)
        doAnswer(invocation -> {
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(partyPresenceFilter).doFilter(any(ServletRequest.class), any(ServletResponse.class), any(FilterChain.class));

        // Create guest user
        guest = User.builder()
                .username("guest")
                .passwordHash(passwordEncoder.encode("guestpass"))
                .credits(5)
                .role(User.Role.USER)
                .build();
        guest = userRepository.save(guest);

        // Create admin user
        admin = User.builder()
                .username("admin")
                .passwordHash(passwordEncoder.encode("adminpass"))
                .credits(0)
                .role(User.Role.ADMIN)
                .build();
        admin = userRepository.save(admin);

        // Create party
        Party party = Party.builder()
                .code("TEST01")
                .hostUserId(admin.getId())
                .partyIp("192.168.1.100")
                .latitude(40.7128)
                .longitude(-74.0060)
                .musicLibraryPath("/music")
                .isActive(true)
                .build();
        party = partyRepository.save(party);
        partyId = party.getId();

        // Create track with required filePath field
        Track track = Track.builder()
                .title("Test Song")
                .artist("Test Artist")
                .album("Test Album")
                .durationSeconds(180)
                .filePath("/music/test-song.mp3")
                .build();
        track = trackRepository.save(track);
        trackId = track.getId();

        // Register guest as party member
        PartyGuest partyGuest = PartyGuest.builder()
                .partyId(partyId)
                .userId(guest.getId())
                .joinedIp(GUEST_IP)
                .build();
        partyGuestRepository.save(partyGuest);

        // Generate tokens
        guestToken = jwtUtil.generateToken(guest.getId(), "guest", "USER");
        adminToken = jwtUtil.generateToken(admin.getId(), "admin", "ADMIN");
    }

    @Test
    public void testAddToQueue_WithoutAuth_ShouldReturn403() throws Exception {
        mockMvc.perform(post("/api/queue/add")
                .header("X-Party-ID", partyId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"trackId\":\"" + trackId.toString() + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    public void testGetQueue_WithPartyId_ShouldReturnEmptyList() throws Exception {
        mockMvc.perform(get("/api/queue")
                .param("partyId", partyId.toString())
                .header("Authorization", "Bearer " + guestToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
