package com.jukebox.live.service;

import com.jukebox.live.entity.Party;
import com.jukebox.live.entity.PartyGuest;
import com.jukebox.live.entity.User;
import com.jukebox.live.repository.PartyGuestRepository;
import com.jukebox.live.repository.PartyRepository;
import com.jukebox.live.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DataJpaTest
@ActiveProfiles("test")
public class PartyServiceTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        public PartyService partyService(PartyRepository partyRepository, PartyGuestRepository partyGuestRepository, UserRepository userRepository, GeofenceService geofenceService) {
            return new PartyService(partyRepository, partyGuestRepository, userRepository, geofenceService);
        }

        @Bean
        public GeofenceService geofenceService() {
            return mock(GeofenceService.class);
        }
    }

    @Autowired
    private PartyRepository partyRepository;

    @Autowired
    private PartyGuestRepository partyGuestRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PartyService partyService;

    @Test
    public void testGetGuests_ReturnsGuestsForParty() {
        // Create party
        Party party = Party.builder()
                .code("TEST01")
                .hostUserId(UUID.randomUUID())
                .partyIp("192.168.1.100")
                .latitude(40.7128)
                .longitude(-74.0060)
                .musicLibraryPath("/music")
                .isActive(true)
                .build();
        partyRepository.save(party);

        // Create guest users
        User guest1 = User.builder()
                .username("guest1")
                .passwordHash("hash")
                .credits(5)
                .role(User.Role.USER)
                .build();
        userRepository.save(guest1);

        User guest2 = User.builder()
                .username("guest2")
                .passwordHash("hash")
                .credits(10)
                .role(User.Role.USER)
                .build();
        userRepository.save(guest2);

        // Add guests to party
        PartyGuest pg1 = PartyGuest.builder()
                .partyId(party.getId())
                .userId(guest1.getId())
                .joinedIp("192.168.1.101")
                .joinedLat(40.7130)
                .joinedLng(-74.0065)
                .build();
        partyGuestRepository.save(pg1);

        PartyGuest pg2 = PartyGuest.builder()
                .partyId(party.getId())
                .userId(guest2.getId())
                .joinedIp("192.168.1.102")
                .joinedLat(40.7125)
                .joinedLng(-74.0055)
                .build();
        partyGuestRepository.save(pg2);

        // Get guests
        var guests = partyService.getGuests(party.getId());

        // Verify both guests returned
        assertThat(guests).hasSize(2);
        assertThat(guests).anyMatch(g -> g.getUsername().equals("guest1") && g.getCredits() == 5);
        assertThat(guests).anyMatch(g -> g.getUsername().equals("guest2") && g.getCredits() == 10);
    }

    @Test
    public void testGetGuests_DifferentParty_ReturnsEmpty() {
        // Create party
        Party party1 = Party.builder()
                .code("PARTY1")
                .hostUserId(UUID.randomUUID())
                .partyIp("192.168.1.100")
                .latitude(40.7128)
                .longitude(-74.0060)
                .musicLibraryPath("/music")
                .isActive(true)
                .build();
        partyRepository.save(party1);

        Party party2 = Party.builder()
                .code("PARTY2")
                .hostUserId(UUID.randomUUID())
                .partyIp("192.168.1.100")
                .latitude(40.7128)
                .longitude(-74.0060)
                .musicLibraryPath("/music")
                .isActive(true)
                .build();
        partyRepository.save(party2);

        // Create guest
        User guest = User.builder()
                .username("guest")
                .passwordHash("hash")
                .credits(5)
                .role(User.Role.USER)
                .build();
        userRepository.save(guest);

        // Add guest to party1 only
        PartyGuest pg = PartyGuest.builder()
                .partyId(party1.getId())
                .userId(guest.getId())
                .joinedIp("192.168.1.101")
                .joinedLat(40.7130)
                .joinedLng(-74.0065)
                .build();
        partyGuestRepository.save(pg);

        // Get guests for party2 should be empty
        var guests = partyService.getGuests(party2.getId());
        assertThat(guests).isEmpty();
    }

    @Test
    public void testGetGuests_WithNullCoordinates_ReturnsGuestsWithNADistance() {
        // Create a real GeofenceService for this test
        GeofenceService realGeofenceService = new GeofenceService();
        
        // Create party with null coordinates (GPS not configured)
        Party party = Party.builder()
                .code("TEST01")
                .hostUserId(UUID.randomUUID())
                .partyIp("192.168.1.100")
                .latitude(null)
                .longitude(null)
                .musicLibraryPath("/music")
                .isActive(true)
                .build();
        partyRepository.save(party);

        // Create guest with null coordinates
        User guest = User.builder()
                .username("guest")
                .passwordHash("hash")
                .credits(5)
                .role(User.Role.USER)
                .build();
        userRepository.save(guest);

        // Add guest to party with null coordinates
        PartyGuest pg = PartyGuest.builder()
                .partyId(party.getId())
                .userId(guest.getId())
                .joinedIp("192.168.1.101")
                .joinedLat(null)
                .joinedLng(null)
                .build();
        partyGuestRepository.save(pg);

        // Create a new PartyService instance with real GeofenceService
        PartyService partyServiceWithRealGeofence = new PartyService(
                partyRepository, partyGuestRepository, userRepository, realGeofenceService);

        // Get guests should return guest with N/A distance instead of throwing error
        var guests = partyServiceWithRealGeofence.getGuests(party.getId());
        assertThat(guests).hasSize(1);
        assertThat(guests.get(0).getUsername()).isEqualTo("guest");
        assertThat(guests.get(0).getCredits()).isEqualTo(5);
        assertThat(guests.get(0).getDistanceFromParty()).isEqualTo("N/A");
    }
}
