package com.jukebox.live.controller;

import com.jukebox.live.dto.*;
import com.jukebox.live.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @GetMapping("/setup-status")
    public ResponseEntity<SetupStatusResponse> getSetupStatus() {
        return ResponseEntity.ok(authService.getSetupStatus());
    }

    @PostMapping("/create-first-admin")
    public ResponseEntity<AuthResponse> createFirstAdmin(@Valid @RequestBody AuthRequest request) {
        return ResponseEntity.ok(authService.createFirstAdmin(request.getUsername(), request.getPassword()));
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody AuthRequest request) {
        return ResponseEntity.ok(authService.register(request.getUsername(), request.getPassword()));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String clientIp = getClientIp(httpRequest);
        return ResponseEntity.ok(authService.login(request, clientIp));
    }

    @PostMapping("/admin-login")
    public ResponseEntity<AuthResponse> adminLogin(@Valid @RequestBody AuthRequest request) {
        return ResponseEntity.ok(authService.adminLogin(request.getUsername(), request.getPassword()));
    }

    private String getClientIp(HttpServletRequest request) {
        // Do not trust X-Forwarded-For header as it can be spoofed by clients
        // Use only the direct connection IP for local network enforcement
        return request.getRemoteAddr();
    }
}
