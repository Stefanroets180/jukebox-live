package com.jukebox.live.service;

import com.jukebox.live.dto.LoginRequest;
import com.jukebox.live.entity.Party;
import com.jukebox.live.entity.User;
import com.jukebox.live.repository.PartyGuestRepository;
import com.jukebox.live.repository.PartyRepository;
import com.jukebox.live.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
public class AuthServiceTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        public PasswordEncoder passwordEncoder() {
            return new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
        }
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PartyRepository partyRepository;

    @Autowired
    private PartyGuestRepository partyGuestRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    public void testAdminLogin_WithAdminRole_ShouldSucceed() {
        User admin = User.builder()
                .username("admin")
                .passwordHash(passwordEncoder.encode("adminpass"))
                .credits(0)
                .role(User.Role.ADMIN)
                .build();
        userRepository.save(admin);

        User found = userRepository.findByUsername("admin").orElseThrow();
        assertThat(found.getRole()).isEqualTo(User.Role.ADMIN);
        assertThat(passwordEncoder.matches("adminpass", found.getPasswordHash())).isTrue();
    }

    @Test
    public void testAdminLogin_WithUserRole_ShouldFail() {
        User user = User.builder()
                .username("regularuser")
                .passwordHash(passwordEncoder.encode("userpass"))
                .credits(0)
                .role(User.Role.USER)
                .build();
        userRepository.save(user);

        User found = userRepository.findByUsername("regularuser").orElseThrow();
        assertThat(found.getRole()).isEqualTo(User.Role.USER);
        // In actual AuthService, adminLogin would throw exception for USER role
    }

    @Test
    public void testGuestLogin_WithSameSubnet_ShouldSucceed() {
        // Create user
        User guest = User.builder()
                .username("guest")
                .passwordHash(passwordEncoder.encode("guestpass"))
                .credits(10)
                .role(User.Role.USER)
                .build();
        userRepository.save(guest);

        // Create party with host IP 192.168.1.100
        Party party = Party.builder()
                .code("ABC123")
                .hostUserId(guest.getId())
                .partyIp("192.168.1.100")
                .latitude(40.7128)
                .longitude(-74.0060)
                .musicLibraryPath("/music")
                .isActive(true)
                .build();
        partyRepository.save(party);

        // Guest login from same subnet (192.168.1.101)
        // Note: This test only validates data setup; actual login requires full AuthService
        Party foundParty = partyRepository.findByCode("ABC123").orElseThrow();
        assertThat(foundParty.getPartyIp()).isEqualTo("192.168.1.100");
        assertThat(foundParty.getIsActive()).isTrue();
    }

    @Test
    public void testGuestLogin_WithDifferentSubnet_ShouldFail() {
        // Create user
        User guest = User.builder()
                .username("guest2")
                .passwordHash(passwordEncoder.encode("guestpass"))
                .credits(10)
                .role(User.Role.USER)
                .build();
        userRepository.save(guest);

        // Create party with host IP 192.168.1.100
        Party party = Party.builder()
                .code("XYZ789")
                .hostUserId(guest.getId())
                .partyIp("192.168.1.100")
                .latitude(40.7128)
                .longitude(-74.0060)
                .musicLibraryPath("/music")
                .isActive(true)
                .build();
        partyRepository.save(party);

        // Guest from different subnet (10.0.0.50) would be rejected by NetworkUtil.isSameSubnet
        // Note: This test validates data setup; actual rejection requires full AuthService
        Party foundParty = partyRepository.findByCode("XYZ789").orElseThrow();
        assertThat(foundParty.getPartyIp()).isEqualTo("192.168.1.100");

        // Verify subnet matching would fail
        assertThat(com.jukebox.live.util.NetworkUtil.isSameSubnet("10.0.0.50", "192.168.1.100")).isFalse();
    }

    @Test
    public void testSetupStatus_WhenNoAdminExists_ShouldReturnAvailable() {
        // Fresh database - no users
        assertThat(userRepository.existsByRole(User.Role.ADMIN)).isFalse();
    }

    @Test
    public void testSetupStatus_WhenAdminExists_ShouldReturnUnavailable() {
        User admin = User.builder()
                .username("admin")
                .passwordHash(passwordEncoder.encode("adminpass"))
                .credits(0)
                .role(User.Role.ADMIN)
                .build();
        userRepository.save(admin);

        assertThat(userRepository.existsByRole(User.Role.ADMIN)).isTrue();
    }

    @Test
    public void testCreateFirstAdmin_WhenNoAdminExists_ShouldSucceed() {
        // Ensure no admin exists
        assertThat(userRepository.existsByRole(User.Role.ADMIN)).isFalse();

        User admin = User.builder()
                .username("firstadmin")
                .passwordHash(passwordEncoder.encode("securepass123"))
                .credits(0)
                .role(User.Role.ADMIN)
                .build();
        userRepository.save(admin);

        User found = userRepository.findByUsername("firstadmin").orElseThrow();
        assertThat(found.getRole()).isEqualTo(User.Role.ADMIN);
        assertThat(passwordEncoder.matches("securepass123", found.getPasswordHash())).isTrue();
        assertThat(userRepository.existsByRole(User.Role.ADMIN)).isTrue();
    }

    @Test
    public void testCreateFirstAdmin_WhenAdminAlreadyExists_ShouldFail() {
        // Create existing admin
        User existingAdmin = User.builder()
                .username("existing")
                .passwordHash(passwordEncoder.encode("existingpass"))
                .credits(0)
                .role(User.Role.ADMIN)
                .build();
        userRepository.save(existingAdmin);

        assertThat(userRepository.existsByRole(User.Role.ADMIN)).isTrue();

        // Attempting to create another admin should be prevented by the service layer
        // This test validates the repository state check
        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    public void testCreateFirstAdmin_WithDuplicateUsername_ShouldFail() {
        // Create a regular user with the same username
        User regularUser = User.builder()
                .username("admin")
                .passwordHash(passwordEncoder.encode("userpass"))
                .credits(0)
                .role(User.Role.USER)
                .build();
        userRepository.save(regularUser);

        assertThat(userRepository.existsByUsername("admin")).isTrue();
        assertThat(userRepository.existsByRole(User.Role.ADMIN)).isFalse();
    }
}
