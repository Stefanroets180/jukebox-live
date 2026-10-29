package com.jukebox.live.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class PlaybackProgressRequest {
    @NotNull
    private UUID queueItemId;

    @NotNull
    private UUID playerInstanceId;

    @NotNull
    @Min(0)
    private Integer progressSeconds;
}
