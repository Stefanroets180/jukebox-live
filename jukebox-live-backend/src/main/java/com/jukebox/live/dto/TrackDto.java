package com.jukebox.live.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TrackDto {
    private String id;
    private String title;
    private String artist;
    private String album;
    private Integer durationSeconds;
    private String albumArtPath;
    private String genre;
    private String musicBrainzReleaseId;
    private String artworkLookupStatus;
    private String artworkSourceUrl;
}
