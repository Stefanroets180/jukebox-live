package com.jukebox.live.dto;

import lombok.Data;

@Data
public class GrantCreditsRequest {
    private String username;
    private Integer amount;
}
