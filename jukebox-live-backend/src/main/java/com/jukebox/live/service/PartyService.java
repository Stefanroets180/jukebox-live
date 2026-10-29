package com.jukebox.live.service;

import com.jukebox.live.dto.*;
import com.jukebox.live.entity.Party;
import com.jukebox.live.entity.PartyGuest;
import com.jukebox.live.entity.User;
import com.jukebox.live.repository.PartyGuestRepository;
import com.jukebox.live.repository.PartyRepository;
import com.jukebox.live.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PartyService {

    private final PartyRepository partyRepository;
    private final PartyGuestRepository partyGuestRepository;
    private final UserRepository userRepository;
    private final GeofenceService geofenceService;
    private final SecureRandom random = new SecureRandom();

    @Transactional
    public PartyResponse createParty(UUID hostUserId, CreatePartyRequest request, String clientIp) {
        String code = generatePartyCode();
        Party party = Party.builder()
                .code(code)
                .hostUserId(hostUserId)
                .partyIp(clientIp)
                .latitude(request.getHostLatitude())
                .longitude(request.getHostLongitude())
                .musicLibraryPath(request.getMusicLibraryPath())
                .isActive(true)
                .build();
        partyRepository.save(party);
        return mapToResponse(party);
    }

    public List<PartyGuestDto> getGuests(UUID partyId) {
        List<PartyGuest> guests = partyGuestRepository.findByPartyId(partyId);
        Party party = partyRepository.findById(partyId).orElseThrow();
        return guests.stream().map(g -> {
            User user = userRepository.findById(g.getUserId()).orElseThrow();
            String distance;
            try {
                distance = Math.round(geofenceService.calculateDistance(
                        g.getJoinedLat(), g.getJoinedLng(),
                        party.getLatitude(), party.getLongitude())) + "m";
            } catch (IllegalArgumentException e) {
                // GPS coordinates not available - show placeholder
                distance = "N/A";
            }
            return PartyGuestDto.builder()
                    .username(user.getUsername())
                    .credits(user.getCredits())
                    .distanceFromParty(distance)
                    .joinedAt(g.getJoinedAt().toString())
                    .build();
        }).collect(Collectors.toList());
    }

    private String generatePartyCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private PartyResponse mapToResponse(Party party) {
        return PartyResponse.builder()
                .id(party.getId().toString())
                .code(party.getCode())
                .partyIp(party.getPartyIp())
                .latitude(party.getLatitude())
                .longitude(party.getLongitude())
                .isActive(party.getIsActive())
                .build();
    }
}
