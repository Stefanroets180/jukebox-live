package com.jukebox.live.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SetupStatusResponse {
    private boolean available;
}
