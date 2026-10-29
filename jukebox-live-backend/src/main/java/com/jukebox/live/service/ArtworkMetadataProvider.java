package com.jukebox.live.service;

import com.jukebox.live.dto.ArtworkCandidateDto;
import com.jukebox.live.entity.Track;

import java.util.List;
import java.util.Optional;

public interface ArtworkMetadataProvider {
    List<ArtworkCandidateDto> findCandidates(Track track);

    Optional<String> getFrontCoverUrl(String releaseId);
}
