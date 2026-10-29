package com.jukebox.live.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jukebox.live.dto.ArtworkCandidateDto;
import com.jukebox.live.dto.ArtworkLookupResponse;
import com.jukebox.live.entity.ArtworkLookupStatus;
import com.jukebox.live.event.ArtworkUpdatedEvent;
import com.jukebox.live.entity.Track;
import com.jukebox.live.repository.TrackRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArtworkDiscoveryServiceTest {

    private static final String COVER_URL = "https://coverartarchive.org/release/11111111-1111-4111-8111-111111111111/front-500";
    private static final String RELEASE_ONE = "11111111-1111-4111-8111-111111111111";
    private static final String RELEASE_TWO = "22222222-2222-4222-8222-222222222222";

    @Mock
    private TrackRepository trackRepository;

    @Mock
    private ArtworkMetadataProvider artworkMetadataProvider;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ArtworkDiscoveryService service;
    private UUID trackId;

    @BeforeEach
    void setUp() {
        service = new ArtworkDiscoveryService(trackRepository, artworkMetadataProvider, new ObjectMapper(), eventPublisher);
        trackId = UUID.randomUUID();
    }

    @Test
    void exactAlbumMatchIsPersisted() {
        Track track = track("Night Drive", ArtworkLookupStatus.NOT_LOOKED_UP, null);
        when(trackRepository.findById(trackId)).thenReturn(Optional.of(track));
        when(trackRepository.save(any(Track.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(artworkMetadataProvider.findCandidates(track)).thenReturn(List.of(
                candidate(RELEASE_ONE, "Night Drive", "Artist One", "First Album", 98),
                candidate(RELEASE_TWO, "Night Drive", "Artist One", "Night Drive", 96)));

        ArtworkLookupResponse result = service.lookup(trackId, false);

        assertEquals("FOUND", result.getStatus());
        assertEquals(RELEASE_TWO, result.getMusicBrainzReleaseId());
        assertEquals(coverUrl(RELEASE_TWO), result.getAlbumArtPath());
        verify(trackRepository).save(track);
        verify(eventPublisher).publishEvent(any(ArtworkUpdatedEvent.class));
        verify(artworkMetadataProvider, never()).getFrontCoverUrl(any());
    }

    @Test
    void ambiguousReleasesAreSavedForManualReview() {
        Track track = track(null, ArtworkLookupStatus.NOT_LOOKED_UP, null);
        when(trackRepository.findById(trackId)).thenReturn(Optional.of(track));
        when(trackRepository.save(any(Track.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(artworkMetadataProvider.findCandidates(track)).thenReturn(List.of(
                candidate(RELEASE_ONE, "Night Drive", "Artist One", "First Album", 98),
                candidate(RELEASE_TWO, "Night Drive", "Artist One", "Second Album", 96)));

        ArtworkLookupResponse result = service.lookup(trackId, false);

        assertEquals("AMBIGUOUS", result.getStatus());
        assertEquals(2, result.getCandidates().size());
        assertEquals(ArtworkLookupStatus.AMBIGUOUS, track.getArtworkLookupStatus());
        assertTrue(track.getArtworkCandidatesJson().contains(RELEASE_ONE));
        verify(trackRepository).save(track);
    }

    @Test
    void onlyHighConfidenceExactTitleAndArtistCandidatesAreEligible() {
        Track track = track("First Album", ArtworkLookupStatus.NOT_LOOKED_UP, null);
        when(trackRepository.findById(trackId)).thenReturn(Optional.of(track));
        when(trackRepository.save(any(Track.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(artworkMetadataProvider.findCandidates(track)).thenReturn(List.of(
                candidate(RELEASE_ONE, "Night Drive (Live)", "Artist One", "First Album", 99),
                candidate(RELEASE_TWO, "Night Drive", "Different Artist", "First Album", 99),
                candidate("33333333-3333-4333-8333-333333333333", "Night Drive", "Artist One", "First Album", 84),
                candidate("44444444-4444-4444-8444-444444444444", "night-drive", "ARTIST ONE", "First Album", 85)));

        ArtworkLookupResponse result = service.lookup(trackId, false);

        assertEquals("FOUND", result.getStatus());
        assertEquals("44444444-4444-4444-8444-444444444444", result.getMusicBrainzReleaseId());
        assertEquals(coverUrl("44444444-4444-4444-8444-444444444444"), result.getAlbumArtPath());
    }

    @Test
    void knownReleaseUsesCoverArchiveWithoutSearching() {
        Track track = track("Night Drive", ArtworkLookupStatus.NOT_LOOKED_UP, RELEASE_ONE);
        when(trackRepository.findById(trackId)).thenReturn(Optional.of(track));
        when(trackRepository.save(any(Track.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(artworkMetadataProvider.getFrontCoverUrl(RELEASE_ONE)).thenReturn(Optional.of(COVER_URL));

        ArtworkLookupResponse result = service.lookup(trackId, false);

        assertEquals("FOUND", result.getStatus());
        assertEquals(RELEASE_ONE, result.getMusicBrainzReleaseId());
        assertEquals(COVER_URL, result.getAlbumArtPath());
        verify(artworkMetadataProvider, never()).findCandidates(any());
    }

    @Test
    void providerFailureIsStoredAsRetryableError() {
        Track track = track("Night Drive", ArtworkLookupStatus.NOT_LOOKED_UP, null);
        when(trackRepository.findById(trackId)).thenReturn(Optional.of(track));
        when(trackRepository.save(any(Track.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(artworkMetadataProvider.findCandidates(track)).thenThrow(new ArtworkProviderException("MusicBrainz unavailable."));

        ArtworkLookupResponse result = service.lookup(trackId, false);

        assertEquals("ERROR", result.getStatus());
        assertEquals("MusicBrainz unavailable.", result.getMessage());
        assertEquals(ArtworkLookupStatus.ERROR, track.getArtworkLookupStatus());
        verify(trackRepository).save(track);
    }

    @Test
    void cachedNotFoundResultIsNotQueriedAgainWithoutRetry() {
        Track track = track("Night Drive", ArtworkLookupStatus.NOT_FOUND, null);
        when(trackRepository.findById(trackId)).thenReturn(Optional.of(track));

        ArtworkLookupResponse result = service.lookup(trackId, false);

        assertEquals("NOT_FOUND", result.getStatus());
        verify(artworkMetadataProvider, never()).findCandidates(any());
        verify(trackRepository, never()).save(any(Track.class));
    }

    @Test
    void manualSelectionOnlyAcceptsPreviouslyReviewedRelease() {
        Track track = track(null, ArtworkLookupStatus.NOT_LOOKED_UP, null);
        when(trackRepository.findById(trackId)).thenReturn(Optional.of(track));
        when(trackRepository.save(any(Track.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(artworkMetadataProvider.findCandidates(track)).thenReturn(List.of(
                candidate(RELEASE_ONE, "Night Drive", "Artist One", "First Album", 98),
                candidate(RELEASE_TWO, "Night Drive", "Artist One", "Second Album", 96)));
        when(artworkMetadataProvider.getFrontCoverUrl(RELEASE_ONE)).thenReturn(Optional.of(COVER_URL));

        service.lookup(trackId, false);
        ArtworkLookupResponse result = service.selectRelease(trackId, RELEASE_ONE);

        assertEquals("FOUND", result.getStatus());
        assertEquals(RELEASE_ONE, track.getMusicBrainzReleaseId());
        assertEquals(COVER_URL, track.getAlbumArtPath());
        verify(trackRepository, times(2)).save(track);
    }

    @Test
    void manualSelectionRejectsReleaseOutsideCurrentCandidateList() {
        Track track = track(null, ArtworkLookupStatus.NOT_LOOKED_UP, null);
        when(trackRepository.findById(trackId)).thenReturn(Optional.of(track));
        when(trackRepository.save(any(Track.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(artworkMetadataProvider.findCandidates(track)).thenReturn(List.of(
                candidate(RELEASE_ONE, "Night Drive", "Artist One", "First Album", 98),
                candidate(RELEASE_TWO, "Night Drive", "Artist One", "Second Album", 96)));

        service.lookup(trackId, false);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.selectRelease(trackId, UUID.randomUUID().toString()));
        assertEquals(400, exception.getStatusCode().value());
    }

    private Track track(String album, ArtworkLookupStatus status, String releaseId) {
        return Track.builder()
                .id(trackId)
                .title("Night Drive")
                .artist("Artist One")
                .album(album)
                .filePath("/music/night-drive.mp3")
                .artworkLookupStatus(status)
                .musicBrainzReleaseId(releaseId)
                .build();
    }

    private String coverUrl(String releaseId) {
        return "https://coverartarchive.org/release/" + releaseId + "/front-500";
    }

    private ArtworkCandidateDto candidate(String releaseId, String recordingTitle, String artist,
            String releaseTitle, int score) {
        return ArtworkCandidateDto.builder()
                .recordingId("33333333-3333-4333-8333-333333333333")
                .recordingTitle(recordingTitle)
                .releaseId(releaseId)
                .releaseTitle(releaseTitle)
                .artist(artist)
                .score(score)
                .coverArtUrl(coverUrl(releaseId))
                .sourceUrl("https://musicbrainz.org/release/" + releaseId)
                .build();
    }
}
