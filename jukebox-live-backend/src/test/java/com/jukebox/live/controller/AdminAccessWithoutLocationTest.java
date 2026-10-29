package com.jukebox.live.controller;

import com.jukebox.live.entity.User;
import com.jukebox.live.repository.UserRepository;
import com.jukebox.live.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class AdminAccessWithoutLocationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String adminToken;

    @BeforeEach
    void setup() {
        // Create admin user
        User admin = User.builder()
                .username("admin")
                .passwordHash(passwordEncoder.encode("adminpass"))
                .credits(0)
                .role(User.Role.ADMIN)
                .build();
        userRepository.save(admin);

        // Generate admin token
        adminToken = jwtUtil.generateToken(admin.getId(), "admin", "ADMIN");
    }

    @Test
    public void testAdminLibraryStats_WithoutLocationHeaders_ShouldSucceed() throws Exception {
        mockMvc.perform(get("/api/library/stats")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").exists());
    }

    @Test
    public void testAdminLibrarySearch_WithoutLocationHeaders_ShouldSucceed() throws Exception {
        mockMvc.perform(get("/api/library/search")
                .param("q", "test")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    public void testAdminScan_WithoutLocationHeaders_ShouldSucceed() throws Exception {
        mockMvc.perform(post("/api/library/scan")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"path\":\"/tmp/test\"}"))
                .andExpect(status().isBadRequest()); // Path doesn't exist, but auth passes
    }

    @Test
    public void testUnauthenticatedRequest_ShouldNotReturnMockData() throws Exception {
        mockMvc.perform(get("/api/library/stats")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden()); // Library endpoints require auth
    }

    @Test
    public void testLibraryStats_WithoutAuth_Returns403() throws Exception {
        mockMvc.perform(get("/api/library/stats"))
                .andExpect(status().isForbidden());
    }

    @Test
    public void testPartyCreation_WithoutGeolocation_ShouldSucceed() throws Exception {
        mockMvc.perform(post("/api/parties/create")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"hostLatitude\":null,\"hostLongitude\":null,\"musicLibraryPath\":\"/tmp/test\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").exists())
                .andExpect(jsonPath("$.id").exists());
    }

    @Test
    public void testPartyCreation_WithGeolocation_ShouldSucceed() throws Exception {
        mockMvc.perform(post("/api/parties/create")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"hostLatitude\":40.7128,\"hostLongitude\":-74.0060,\"musicLibraryPath\":\"/tmp/test\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").exists())
                .andExpect(jsonPath("$.id").exists());
    }
}
