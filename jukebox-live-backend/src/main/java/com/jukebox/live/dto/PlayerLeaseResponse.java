package com.jukebox.live.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class PlayerLeaseResponse {
    private boolean acquired;
    private Instant expiresAt;
    private String message;
}
