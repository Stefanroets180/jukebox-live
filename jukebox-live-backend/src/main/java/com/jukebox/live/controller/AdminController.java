package com.jukebox.live.controller;

import com.jukebox.live.dto.GrantCreditsRequest;
import com.jukebox.live.entity.User;
import com.jukebox.live.service.CreditService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final CreditService creditService;

    @PostMapping("/credits/grant")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> grantCredits(
            @AuthenticationPrincipal User admin,
            @RequestBody GrantCreditsRequest request,
            @RequestHeader("X-Party-ID") UUID partyId) {
        creditService.grantCredits(admin.getId(), request.getUsername(), request.getAmount(), partyId);
        return ResponseEntity.ok().build();
    }
}
