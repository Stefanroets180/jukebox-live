package com.jukebox.live.controller;

import com.jukebox.live.dto.PartySettingsRequest;
import com.jukebox.live.dto.PartySettingsResponse;
import com.jukebox.live.entity.Party;
import com.jukebox.live.entity.User;
import com.jukebox.live.repository.PartyRepository;
import com.jukebox.live.repository.UserRepository;
import com.jukebox.live.security.JwtUtil;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PartyRepository partyRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private Validator validator;

    private User hostUser;
    private User otherAdmin;
    private User guestUser;
    private String hostToken;
    private String otherAdminToken;
    private String guestToken;
    private UUID partyId;

    @BeforeEach
    void setup() {
        // Create host admin user
        hostUser = User.builder()
                .username("hostadmin")
                .passwordHash(passwordEncoder.encode("hostpass"))
                .credits(10)
                .role(User.Role.ADMIN)
                .build();
        hostUser = userRepository.save(hostUser);
        hostToken = jwtUtil.generateToken(hostUser.getId(), "hostadmin", "ADMIN");

        // Create another admin user (not the host)
        otherAdmin = User.builder()
                .username("otheradmin")
                .passwordHash(passwordEncoder.encode("otherpass"))
                .credits(10)
                .role(User.Role.ADMIN)
                .build();
        otherAdmin = userRepository.save(otherAdmin);
        otherAdminToken = jwtUtil.generateToken(otherAdmin.getId(), "otheradmin", "ADMIN");

        // Create a guest user (USER role)
        guestUser = User.builder()
                .username("guestuser")
                .passwordHash(passwordEncoder.encode("guestpass"))
                .credits(5)
                .role(User.Role.USER)
                .build();
        guestUser = userRepository.save(guestUser);
        guestToken = jwtUtil.generateToken(guestUser.getId(), "guestuser", "USER");

        // Create a party hosted by the host user
        Party party = Party.builder()
                .code("TESTPARTY")
                .hostUserId(hostUser.getId())
                .partyIp("192.168.1.100")
                .musicLibraryPath("/music")
                .isActive(true)
                .creditsValidDays(30)
                .build();
        party = partyRepository.save(party);
        partyId = party.getId();
    }

    @Test
    public void testGetPartySettings_ReturnsCurrentConfiguredValue() throws Exception {
        mockMvc.perform(get("/api/admin/party-settings")
                        .header("Authorization", "Bearer " + hostToken)
                        .header("X-Party-ID", partyId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creditsValidDays").value(30));
    }

    @Test
    public void testUpdatePartySettings_UpdatesValueAndPersists() throws Exception {
        mockMvc.perform(post("/api/admin/party-settings")
                        .header("Authorization", "Bearer " + hostToken)
                        .header("X-Party-ID", partyId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"creditsValidDays\": 45}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creditsValidDays").value(45));

        // Verify persistence by fetching again
        mockMvc.perform(get("/api/admin/party-settings")
                        .header("Authorization", "Bearer " + hostToken)
                        .header("X-Party-ID", partyId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creditsValidDays").value(45));

        // Verify in database
        Party party = partyRepository.findById(partyId).orElseThrow();
        assertThat(party.getCreditsValidDays()).isEqualTo(45);
    }

    @Test
    public void testUpdatePartySettings_ValidityPeriod1Accepted() throws Exception {
        mockMvc.perform(post("/api/admin/party-settings")
                        .header("Authorization", "Bearer " + hostToken)
                        .header("X-Party-ID", partyId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"creditsValidDays\": 1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creditsValidDays").value(1));
    }

    @Test
    public void testUpdatePartySettings_ValidityPeriod100Accepted() throws Exception {
        mockMvc.perform(post("/api/admin/party-settings")
                        .header("Authorization", "Bearer " + hostToken)
                        .header("X-Party-ID", partyId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"creditsValidDays\": 100}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creditsValidDays").value(100));
    }

    // Direct validation test using Jakarta Validator to prove annotations work
    @Test
    public void testPartySettingsRequest_DirectValidation() {
        PartySettingsRequest request1 = new PartySettingsRequest();
        request1.setCreditsValidDays(1);
        Set<jakarta.validation.ConstraintViolation<PartySettingsRequest>> violations1 = validator.validate(request1);
        assertThat(violations1).isEmpty();

        PartySettingsRequest request100 = new PartySettingsRequest();
        request100.setCreditsValidDays(100);
        Set<jakarta.validation.ConstraintViolation<PartySettingsRequest>> violations100 = validator.validate(request100);
        assertThat(violations100).isEmpty();

        PartySettingsRequest request0 = new PartySettingsRequest();
        request0.setCreditsValidDays(0);
        Set<jakarta.validation.ConstraintViolation<PartySettingsRequest>> violations0 = validator.validate(request0);
        assertThat(violations0).isNotEmpty();
        assertThat(violations0).anyMatch(v -> v.getMessage().contains("1") || v.getPropertyPath().toString().equals("creditsValidDays"));

        PartySettingsRequest request101 = new PartySettingsRequest();
        request101.setCreditsValidDays(101);
        Set<jakarta.validation.ConstraintViolation<PartySettingsRequest>> violations101 = validator.validate(request101);
        assertThat(violations101).isNotEmpty();
        assertThat(violations101).anyMatch(v -> v.getMessage().contains("100") || v.getPropertyPath().toString().equals("creditsValidDays"));
    }

    @Test
    public void testUpdatePartySettings_ValidityPeriod0Rejected() throws Exception {
        mockMvc.perform(post("/api/admin/party-settings")
                        .header("Authorization", "Bearer " + hostToken)
                        .header("X-Party-ID", partyId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"creditsValidDays\": 0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void testUpdatePartySettings_ValidityPeriod101Rejected() throws Exception {
        mockMvc.perform(post("/api/admin/party-settings")
                        .header("Authorization", "Bearer " + hostToken)
                        .header("X-Party-ID", partyId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"creditsValidDays\": 101}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void testUpdatePartySettings_UnauthorizedUserDenied() throws Exception {
        mockMvc.perform(post("/api/admin/party-settings")
                        .header("Authorization", "Bearer " + guestToken)
                        .header("X-Party-ID", partyId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"creditsValidDays\": 45}"))
                .andExpect(status().isForbidden());
    }

    @Test
    public void testUpdatePartySettings_NonHostAdminDenied() throws Exception {
        mockMvc.perform(post("/api/admin/party-settings")
                        .header("Authorization", "Bearer " + otherAdminToken)
                        .header("X-Party-ID", partyId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"creditsValidDays\": 45}"))
                .andExpect(status().isForbidden());
    }

    @Test
    public void testGetPartySettings_NonHostAdminDenied() throws Exception {
        mockMvc.perform(get("/api/admin/party-settings")
                        .header("Authorization", "Bearer " + otherAdminToken)
                        .header("X-Party-ID", partyId.toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    public void testGetPartySettings_UnauthenticatedDenied() throws Exception {
        mockMvc.perform(get("/api/admin/party-settings")
                        .header("X-Party-ID", partyId.toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    public void testUpdatePartySettings_FieldNameCompatibleWithUI() throws Exception {
        mockMvc.perform(post("/api/admin/party-settings")
                        .header("Authorization", "Bearer " + hostToken)
                        .header("X-Party-ID", partyId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"creditsValidDays\": 60}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creditsValidDays").exists());
    }
}
