package com.jukebox.live.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class NowPlayingDto {
    private TrackDto track;
    private Integer progressSeconds;
    private Boolean isPlaying;
    private RequesterDto requestedBy;
}
