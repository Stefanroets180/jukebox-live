package com.jukebox.live.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AddToQueueResponse {
    private String id;
    private String trackId;
    private String status;
    private Integer position;
    private Integer cost;
}
