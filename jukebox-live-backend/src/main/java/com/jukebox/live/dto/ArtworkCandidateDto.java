package com.jukebox.live.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArtworkCandidateDto {
    private String recordingId;
    private String recordingTitle;
    private String releaseId;
    private String releaseTitle;
    private String artist;
    private String releaseDate;
    private String country;
    private Integer score;
    private String coverArtUrl;
    private String sourceUrl;
}
