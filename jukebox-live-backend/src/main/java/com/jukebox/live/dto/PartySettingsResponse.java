package com.jukebox.live.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PartySettingsResponse {
    Integer creditsValidDays;
}
