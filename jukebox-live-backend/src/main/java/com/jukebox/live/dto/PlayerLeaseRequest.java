package com.jukebox.live.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class PlayerLeaseRequest {
    @NotNull
    private UUID playerInstanceId;
}
