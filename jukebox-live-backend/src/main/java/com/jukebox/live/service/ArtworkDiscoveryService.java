package com.jukebox.live.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jukebox.live.dto.ArtworkCandidateDto;
import com.jukebox.live.dto.ArtworkLookupResponse;
import com.jukebox.live.entity.ArtworkLookupStatus;
import com.jukebox.live.event.ArtworkUpdatedEvent;
import com.jukebox.live.entity.Track;
import com.jukebox.live.repository.TrackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ArtworkDiscoveryService {
    private static final TypeReference<List<ArtworkCandidateDto>> CANDIDATES_TYPE = new TypeReference<>() {};

    private final TrackRepository trackRepository;
    private final ArtworkMetadataProvider artworkMetadataProvider;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    public ArtworkLookupResponse lookup(UUID trackId, boolean retry) {
        Track track = findTrack(trackId);
        if (!retry && track.getArtworkLookupStatus() != ArtworkLookupStatus.NOT_LOOKED_UP) {
            return response(track, "This track was already checked. Use Retry to query the providers again.");
        }

        if (track.getMusicBrainzReleaseId() != null && !track.getMusicBrainzReleaseId().isBlank()) {
            try {
                var coverUrl = artworkMetadataProvider.getFrontCoverUrl(track.getMusicBrainzReleaseId());
                if (coverUrl.isPresent()) {
                    return markFound(track, track.getMusicBrainzReleaseId(), coverUrl.get(),
                            musicBrainzReleaseUrl(track.getMusicBrainzReleaseId()), List.of());
                }
                return markNotFound(track, List.of(), "No approved front cover is available for the saved MusicBrainz release.");
            } catch (ArtworkProviderException exception) {
                return markError(track, exception.getMessage());
            }
        }

        try {
            List<ArtworkCandidateDto> candidates = artworkMetadataProvider.findCandidates(track).stream()
                    .filter(candidate -> candidate.getScore() != null && candidate.getScore() >= 85)
                    .filter(candidate -> matches(track.getTitle(), candidate.getRecordingTitle()))
                    .filter(candidate -> matches(track.getArtist(), candidate.getArtist()))
                    .toList();

            if (candidates.isEmpty()) {
                return markNotFound(track, List.of(), "No reliable approved cover was found. You can retry later.");
            }

            List<ArtworkCandidateDto> albumMatches = track.getAlbum() == null || track.getAlbum().isBlank()
                    ? List.of()
                    : candidates.stream()
                            .filter(candidate -> matches(track.getAlbum(), candidate.getReleaseTitle()))
                            .toList();

            if (albumMatches.size() == 1) {
                ArtworkCandidateDto match = albumMatches.getFirst();
                return markFound(track, match.getReleaseId(), match.getCoverArtUrl(), match.getSourceUrl(), List.of());
            }

            if (albumMatches.isEmpty() && (track.getAlbum() == null || track.getAlbum().isBlank()) && candidates.size() == 1) {
                ArtworkCandidateDto match = candidates.getFirst();
                return markFound(track, match.getReleaseId(), match.getCoverArtUrl(), match.getSourceUrl(), List.of());
            }

            List<ArtworkCandidateDto> reviewCandidates = albumMatches.isEmpty() ? candidates : albumMatches;
            track.setArtworkLookupStatus(ArtworkLookupStatus.AMBIGUOUS);
            track.setArtworkLastCheckedAt(Instant.now());
            track.setArtworkCandidatesJson(writeCandidates(reviewCandidates));
            trackRepository.save(track);
            return response(track, "Several releases match this track. Select the correct release before saving its cover.");
        } catch (ArtworkProviderException exception) {
            return markError(track, exception.getMessage());
        }
    }

    public ArtworkLookupResponse selectRelease(UUID trackId, String releaseId) {
        Track track = findTrack(trackId);
        List<ArtworkCandidateDto> candidates = readCandidates(track.getArtworkCandidatesJson());
        ArtworkCandidateDto selected = candidates.stream()
                .filter(candidate -> candidate.getReleaseId().equalsIgnoreCase(releaseId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select a release from the current review list."));

        try {
            String coverUrl = artworkMetadataProvider.getFrontCoverUrl(selected.getReleaseId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "The selected release no longer has an approved front cover."));
            return markFound(track, selected.getReleaseId(), coverUrl,
                    selected.getSourceUrl() == null ? musicBrainzReleaseUrl(selected.getReleaseId()) : selected.getSourceUrl(),
                    List.of());
        } catch (ArtworkProviderException exception) {
            return markError(track, exception.getMessage());
        }
    }

    public ArtworkLookupResponse getCandidates(UUID trackId) {
        return response(findTrack(trackId), "Review the saved MusicBrainz matches.");
    }

    private Track findTrack(UUID trackId) {
        return trackRepository.findById(trackId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Track not found."));
    }

    private ArtworkLookupResponse markFound(Track track, String releaseId, String coverUrl, String sourceUrl,
                                             List<ArtworkCandidateDto> candidates) {
        track.setMusicBrainzReleaseId(releaseId);
        track.setAlbumArtPath(coverUrl);
        track.setArtworkSourceUrl(sourceUrl);
        track.setArtworkLookupStatus(ArtworkLookupStatus.FOUND);
        track.setArtworkLastCheckedAt(Instant.now());
        track.setArtworkCandidatesJson(null);
        trackRepository.save(track);
        eventPublisher.publishEvent(new ArtworkUpdatedEvent(this, track.getId()));
        return response(track, "Cover art saved from the Cover Art Archive.");
    }

    private ArtworkLookupResponse markNotFound(Track track, List<ArtworkCandidateDto> candidates, String message) {
        track.setArtworkLookupStatus(ArtworkLookupStatus.NOT_FOUND);
        track.setArtworkLastCheckedAt(Instant.now());
        track.setArtworkCandidatesJson(writeCandidates(candidates));
        trackRepository.save(track);
        return response(track, message);
    }

    private ArtworkLookupResponse markError(Track track, String message) {
        track.setArtworkLookupStatus(ArtworkLookupStatus.ERROR);
        track.setArtworkLastCheckedAt(Instant.now());
        trackRepository.save(track);
        return response(track, message == null ? "Artwork providers are temporarily unavailable." : message);
    }

    private ArtworkLookupResponse response(Track track, String message) {
        return ArtworkLookupResponse.builder()
                .trackId(track.getId().toString())
                .status(track.getArtworkLookupStatus().name())
                .message(message)
                .albumArtPath(track.getAlbumArtPath())
                .musicBrainzReleaseId(track.getMusicBrainzReleaseId())
                .artworkSourceUrl(track.getArtworkSourceUrl())
                .candidates(readCandidates(track.getArtworkCandidatesJson()))
                .build();
    }

    private List<ArtworkCandidateDto> readCandidates(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, CANDIDATES_TYPE);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private String writeCandidates(List<ArtworkCandidateDto> candidates) {
        try {
            return objectMapper.writeValueAsString(candidates);
        } catch (Exception exception) {
            return "[]";
        }
    }

    private static boolean matches(String expected, String actual) {
        return normalize(expected).equals(normalize(actual));
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim();
    }

    private static String musicBrainzReleaseUrl(String releaseId) {
        return "https://musicbrainz.org/release/" + releaseId;
    }
}
