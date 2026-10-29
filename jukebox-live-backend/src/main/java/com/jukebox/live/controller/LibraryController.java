package com.jukebox.live.controller;

import com.jukebox.live.dto.*;
import com.jukebox.live.entity.Track;
import com.jukebox.live.entity.User;
import com.jukebox.live.service.ArtworkDiscoveryService;
import com.jukebox.live.service.LibraryScannerService;
import com.jukebox.live.repository.TrackRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/library")
@RequiredArgsConstructor
public class LibraryController {

    private final LibraryScannerService libraryScannerService;
    private final TrackRepository trackRepository;
    private final ArtworkDiscoveryService artworkDiscoveryService;

    @GetMapping("/search")
    public ResponseEntity<List<TrackDto>> search(@RequestParam String q) {
        return ResponseEntity.ok(trackRepository.search(q).stream()
                .map(t -> TrackDto.builder()
                        .id(t.getId().toString())
                        .title(t.getTitle())
                        .artist(t.getArtist())
                        .album(t.getAlbum())
                        .durationSeconds(t.getDurationSeconds())
                        .albumArtPath(t.getAlbumArtPath())
                        .genre(t.getGenre())
                        .musicBrainzReleaseId(t.getMusicBrainzReleaseId())
                        .artworkLookupStatus(t.getArtworkLookupStatus().name())
                        .artworkSourceUrl(t.getArtworkSourceUrl())
                        .build())
                .collect(Collectors.toList()));
    }

    @GetMapping("/stats")
    public ResponseEntity<LibraryStatsResponse> getStats() {
        try {
            long count = libraryScannerService.getTrackCount();
            return ResponseEntity.ok(LibraryStatsResponse.builder()
                    .count(count)
                    .build());
        } catch (Exception e) {
            return ResponseEntity.ok(LibraryStatsResponse.builder()
                    .count(0L)
                    .build());
        }
    }

    @PostMapping("/scan")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ScanLibraryResponse> scanLibrary(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody ScanLibraryRequest request) {
        int count = libraryScannerService.scanLibrary(request.getPath());
        return ResponseEntity.ok(ScanLibraryResponse.builder().scannedCount(count).build());
    }

    @GetMapping("/artwork/missing")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ArtworkQueueResponse> getMissingArtwork(
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        int safeOffset = Math.max(offset, 0);
        Page<Track> page = trackRepository.findTracksMissingArtwork(
                PageRequest.of(safeOffset / safeLimit, safeLimit));
        return ResponseEntity.ok(ArtworkQueueResponse.builder()
                .tracks(page.getContent().stream().map(this::toTrackDto).toList())
                .total(page.getTotalElements())
                .offset(page.getNumber() * page.getSize())
                .limit(page.getSize())
                .build());
    }

    @PostMapping("/artwork/{trackId}/lookup")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ArtworkLookupResponse> lookupArtwork(
            @PathVariable UUID trackId,
            @RequestParam(defaultValue = "false") boolean retry) {
        return ResponseEntity.ok(artworkDiscoveryService.lookup(trackId, retry));
    }

    @GetMapping("/artwork/{trackId}/candidates")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ArtworkLookupResponse> getArtworkCandidates(@PathVariable UUID trackId) {
        return ResponseEntity.ok(artworkDiscoveryService.getCandidates(trackId));
    }

    @PostMapping("/artwork/{trackId}/select")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ArtworkLookupResponse> selectArtwork(
            @PathVariable UUID trackId,
            @Valid @RequestBody ArtworkSelectionRequest request) {
        return ResponseEntity.ok(artworkDiscoveryService.selectRelease(trackId, request.getReleaseId()));
    }

    private TrackDto toTrackDto(Track track) {
        return TrackDto.builder()
                .id(track.getId().toString())
                .title(track.getTitle())
                .artist(track.getArtist())
                .album(track.getAlbum())
                .durationSeconds(track.getDurationSeconds())
                .albumArtPath(track.getAlbumArtPath())
                .genre(track.getGenre())
                .musicBrainzReleaseId(track.getMusicBrainzReleaseId())
                .artworkLookupStatus(track.getArtworkLookupStatus().name())
                .artworkSourceUrl(track.getArtworkSourceUrl())
                .build();
    }
}
