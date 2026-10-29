package com.jukebox.live.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ArtworkLookupResponse {
    private String trackId;
    private String status;
    private String message;
    private String albumArtPath;
    private String musicBrainzReleaseId;
    private String artworkSourceUrl;
    private List<ArtworkCandidateDto> candidates;
}
