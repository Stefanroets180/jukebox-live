package com.jukebox.live.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PartyResponse {
    private String id;
    private String code;
    private String partyIp;
    private Double latitude;
    private Double longitude;
    private Boolean isActive;
}
