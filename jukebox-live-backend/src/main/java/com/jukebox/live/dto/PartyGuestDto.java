package com.jukebox.live.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PartyGuestDto {
    private String username;
    private Integer credits;
    private String distanceFromParty;
    private String joinedAt;
}
