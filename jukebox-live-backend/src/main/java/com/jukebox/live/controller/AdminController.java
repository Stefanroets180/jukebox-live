package com.jukebox.live.controller;

import com.jukebox.live.dto.GrantCreditsRequest;
import com.jukebox.live.dto.PartySettingsRequest;
import com.jukebox.live.dto.PartySettingsResponse;
import com.jukebox.live.entity.Party;
import com.jukebox.live.entity.User;
import com.jukebox.live.repository.PartyRepository;
import com.jukebox.live.service.CreditService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final CreditService creditService;
    private final PartyRepository partyRepository;

    @PostMapping("/credits/grant")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> grantCredits(
            @AuthenticationPrincipal User admin,
            @RequestBody GrantCreditsRequest request,
            @RequestHeader("X-Party-ID") UUID partyId) {
        creditService.grantCredits(admin.getId(), request.getUsername(), request.getAmount(), partyId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/party-settings")
    @PreAuthorize("hasRole('ADMIN')")
    public PartySettingsResponse getSettings(@AuthenticationPrincipal User admin, @RequestHeader("X-Party-ID") UUID partyId) {
        Party party = hostParty(admin, partyId);
        return PartySettingsResponse.builder().creditsValidDays(party.getCreditsValidDays()).build();
    }

    @PostMapping("/party-settings")
    @PreAuthorize("hasRole('ADMIN')")
    public PartySettingsResponse updateSettings(@AuthenticationPrincipal User admin,
                                                  @RequestHeader("X-Party-ID") UUID partyId,
                                                  @Valid @RequestBody PartySettingsRequest request) {
        Party party = hostParty(admin, partyId);
        party.setCreditsValidDays(request.getCreditsValidDays());
        partyRepository.save(party);
        return PartySettingsResponse.builder().creditsValidDays(party.getCreditsValidDays()).build();
    }

    private Party hostParty(User admin, UUID partyId) {
        Party party = partyRepository.findById(partyId).orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Party not found"));
        if (!party.getHostUserId().equals(admin.getId())) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "Only the party host can change settings");
        }
        return party;
    }
}
