package com.jukebox.live.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ArtworkQueueResponse {
    private List<TrackDto> tracks;
    private long total;
    private int offset;
    private int limit;
}
