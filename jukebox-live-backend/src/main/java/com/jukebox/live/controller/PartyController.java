package com.jukebox.live.controller;

import com.jukebox.live.dto.*;
import com.jukebox.live.entity.User;
import com.jukebox.live.service.PartyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/parties")
@RequiredArgsConstructor
public class PartyController {

    private final PartyService partyService;

    @PostMapping("/create")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PartyResponse> createParty(
            @AuthenticationPrincipal User host,
            @Valid @RequestBody CreatePartyRequest request,
            HttpServletRequest httpRequest) {
        String clientIp = getClientIp(httpRequest);
        return ResponseEntity.ok(partyService.createParty(host.getId(), request, clientIp));
    }

    @GetMapping("/{partyId}/guests")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<PartyGuestDto>> getGuests(@PathVariable UUID partyId) {
        return ResponseEntity.ok(partyService.getGuests(partyId));
    }

    private String getClientIp(HttpServletRequest request) {
        // Do not trust X-Forwarded-For header as it can be spoofed by clients
        // Use only the direct connection IP for local network enforcement
        return request.getRemoteAddr();
    }
}
