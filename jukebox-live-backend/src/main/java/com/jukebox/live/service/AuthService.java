package com.jukebox.live.service;

import com.jukebox.live.dto.*;
import com.jukebox.live.entity.Party;
import com.jukebox.live.entity.PartyGuest;
import com.jukebox.live.entity.User;
import com.jukebox.live.repository.PartyGuestRepository;
import com.jukebox.live.repository.PartyRepository;
import com.jukebox.live.repository.UserRepository;
import com.jukebox.live.security.JwtUtil;
import com.jukebox.live.util.NetworkUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PartyRepository partyRepository;
    private final PartyGuestRepository partyGuestRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final GeofenceService geofenceService;

    public SetupStatusResponse getSetupStatus() {
        boolean available = !userRepository.existsByRole(User.Role.ADMIN);
        return SetupStatusResponse.builder().available(available).build();
    }

    @Transactional
    public AuthResponse createFirstAdmin(String username, String password) {
        // Check atomically within transaction - prevents race conditions
        if (userRepository.existsByRole(User.Role.ADMIN)) {
            throw new RuntimeException("Administrator already exists");
        }

        if (userRepository.existsByUsername(username)) {
            throw new RuntimeException("Username already taken");
        }

        User admin = User.builder()
                .username(username)
                .passwordHash(passwordEncoder.encode(password))
                .credits(0)
                .role(User.Role.ADMIN)
                .build();
        userRepository.save(admin);

        String token = jwtUtil.generateToken(admin.getId(), admin.getUsername(), admin.getRole().name());
        return AuthResponse.builder()
                .token(token)
                .userId(admin.getId().toString())
                .partyId(null)
                .credits(admin.getCredits())
                .build();
    }

    @Transactional
    public RegisterResponse register(String username, String password) {
        if (userRepository.existsByUsername(username)) {
            throw new RuntimeException("Username already taken");
        }
        User user = User.builder()
                .username(username)
                .passwordHash(passwordEncoder.encode(password))
                .credits(0)
                .role(User.Role.USER)
                .build();
        userRepository.save(user);
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole().name());
        return RegisterResponse.builder()
                .token(token)
                .user(UserDto.builder()
                        .id(user.getId().toString())
                        .username(user.getUsername())
                        .credits(user.getCredits())
                        .role(user.getRole().name())
                        .build())
                .build();
    }

    @Transactional
    public AuthResponse login(LoginRequest request, String clientIp) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("Invalid credentials"));
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new RuntimeException("Invalid credentials");
        }

        Party party = partyRepository.findByCode(request.getPartyCode().toUpperCase())
                .orElseThrow(() -> new RuntimeException("Party not found"));
        if (!party.getIsActive()) {
            throw new RuntimeException("Party has ended");
        }

        // Network check - guest must be on the same local network subnet as the party host
        if (!NetworkUtil.isSameSubnet(clientIp, party.getPartyIp())) {
            throw new RuntimeException("You must be on the party WiFi to join");
        }

        // Register guest
        if (!partyGuestRepository.existsByPartyIdAndUserId(party.getId(), user.getId())) {
            PartyGuest guest = PartyGuest.builder()
                    .partyId(party.getId())
                    .userId(user.getId())
                    .joinedIp(clientIp)
                    .joinedLat(null)
                    .joinedLng(null)
                    .build();
            partyGuestRepository.save(guest);
        }

        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole().name());
        return AuthResponse.builder()
                .token(token)
                .userId(user.getId().toString())
                .partyId(party.getId().toString())
                .credits(user.getCredits())
                .build();
    }

    @Transactional
    public AuthResponse adminLogin(String username, String password) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Invalid credentials"));
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new RuntimeException("Invalid credentials");
        }
        if (user.getRole() != User.Role.ADMIN) {
            throw new RuntimeException("Admin access required");
        }

        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole().name());
        return AuthResponse.builder()
                .token(token)
                .userId(user.getId().toString())
                .partyId(null)
                .credits(user.getCredits())
                .build();
    }
}
