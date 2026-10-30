package com.jukebox.live.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class PartySettingsRequest {
    @Min(1)
    @Max(100)
    private Integer creditsValidDays;
}
