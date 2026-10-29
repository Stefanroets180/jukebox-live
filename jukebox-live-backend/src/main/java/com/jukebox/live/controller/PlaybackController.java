package com.jukebox.live.controller;

import com.jukebox.live.dto.*;
import com.jukebox.live.entity.Party;
import com.jukebox.live.entity.QueueItem;
import com.jukebox.live.entity.Track;
import com.jukebox.live.entity.User;
import com.jukebox.live.repository.PartyRepository;
import com.jukebox.live.repository.QueueItemRepository;
import com.jukebox.live.repository.TrackRepository;
import com.jukebox.live.repository.UserRepository;
import com.jukebox.live.security.JwtUtil;
import com.jukebox.live.service.PlaybackLeaseService;
import com.jukebox.live.service.PlaybackService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.File;
import java.io.FilterInputStream;
import java.io.InputStream;
import java.io.EOFException;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/playback")
@RequiredArgsConstructor
@Slf4j
public class PlaybackController {

    private final QueueItemRepository queueItemRepository;
    private final TrackRepository trackRepository;
    private final UserRepository userRepository;
    private final PartyRepository partyRepository;
    private final PlaybackService playbackService;
    private final PlaybackLeaseService playbackLeaseService;
    private final JwtUtil jwtUtil;

    @GetMapping("/now-playing")
    public ResponseEntity<NowPlayingDto> getNowPlaying(@RequestParam UUID partyId) {
        var playing = queueItemRepository.findFirstByPartyIdAndStatusOrderByPositionAsc(partyId, QueueItem.Status.PLAYING);
        if (playing.isPresent()) {
            QueueItem item = playing.get();
            Track track = trackRepository.findById(item.getTrackId()).orElse(null);
            User requester = userRepository.findById(item.getRequestedBy()).orElse(null);
            
            // Handle missing track or user gracefully
            if (track == null || requester == null) {
                // If track or user is missing, skip this item and return null
                return ResponseEntity.ok(null);
            }
            
            return ResponseEntity.ok(NowPlayingDto.builder()
                    .track(TrackDto.builder()
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
                            .build())
                    .progressSeconds(item.getProgressSeconds())
                    .isPlaying(!Boolean.TRUE.equals(item.getIsPaused()))
                    .requestedBy(RequesterDto.builder().username(requester.getUsername()).build())
                    .build());
        }
        return ResponseEntity.ok(null);
    }

    @PostMapping("/start")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> startPlayback(
            @RequestParam UUID partyId,
            @AuthenticationPrincipal User user) {
        requireHost(partyId, user);
        playbackService.startNextTrack(partyId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/complete")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> completePlayback(
            @RequestParam UUID partyId,
            @RequestParam UUID queueItemId,
            @AuthenticationPrincipal User user) {
        requireHost(partyId, user);
        playbackService.completeCurrentTrack(partyId, queueItemId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/skip")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> skipPlayback(
            @RequestParam UUID partyId,
            @AuthenticationPrincipal User user) {
        requireHost(partyId, user);
        playbackService.skipCurrentTrack(partyId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/player-lease/acquire")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PlayerLeaseResponse> acquirePlayerLease(
            @RequestParam UUID partyId,
            @Valid @RequestBody PlayerLeaseRequest request,
            @AuthenticationPrincipal User user) {
        requireHost(partyId, user);
        PlayerLeaseResponse result = playbackLeaseService.acquire(partyId, user.getId(), request.getPlayerInstanceId());
        return ResponseEntity.status(result.isAcquired() ? HttpStatus.OK : HttpStatus.CONFLICT).body(result);
    }

    @PostMapping("/player-lease/heartbeat")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PlayerLeaseResponse> renewPlayerLease(
            @RequestParam UUID partyId,
            @Valid @RequestBody PlayerLeaseRequest request,
            @AuthenticationPrincipal User user) {
        requireHost(partyId, user);
        PlayerLeaseResponse result = playbackLeaseService.renew(partyId, user.getId(), request.getPlayerInstanceId());
        return ResponseEntity.status(result.isAcquired() ? HttpStatus.OK : HttpStatus.CONFLICT).body(result);
    }

    @PostMapping("/player-lease/release")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> releasePlayerLease(
            @RequestParam UUID partyId,
            @Valid @RequestBody PlayerLeaseRequest request,
            @AuthenticationPrincipal User user) {
        requireHost(partyId, user);
        playbackLeaseService.release(partyId, user.getId(), request.getPlayerInstanceId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/progress")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> updateProgress(
            @RequestParam UUID partyId,
            @Valid @RequestBody PlaybackProgressRequest request,
            @AuthenticationPrincipal User user) {
        requireHost(partyId, user);
        requirePlayerLease(partyId, user.getId(), request.getPlayerInstanceId());
        playbackService.updateProgress(partyId, request.getQueueItemId(), request.getProgressSeconds());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/pause")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> pausePlayback(
            @RequestParam UUID partyId,
            @Valid @RequestBody PlayerLeaseRequest request,
            @AuthenticationPrincipal User user) {
        requireHost(partyId, user);
        requirePlayerLease(partyId, user.getId(), request.getPlayerInstanceId());
        playbackService.setPaused(partyId, true);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/resume")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> resumePlayback(
            @RequestParam UUID partyId,
            @Valid @RequestBody PlayerLeaseRequest request,
            @AuthenticationPrincipal User user) {
        requireHost(partyId, user);
        requirePlayerLease(partyId, user.getId(), request.getPlayerInstanceId());
        playbackService.setPaused(partyId, false);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/track-file")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> getTrackFile(@RequestParam UUID queueItemId) {
        Optional<String> filePath = playbackService.getTrackFilePath(queueItemId);
        return filePath.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/audio-token")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> getAudioToken(
            @RequestParam UUID queueItemId,
            @RequestParam UUID partyId,
            @RequestParam UUID playerInstanceId,
            @AuthenticationPrincipal User user) {
        Party party = requireHost(partyId, user);
        requirePlayerLease(partyId, user.getId(), playerInstanceId);
        QueueItem queueItem = queueItemRepository.findById(queueItemId).orElse(null);
        if (queueItem == null || !queueItem.getPartyId().equals(party.getId())) {
            return ResponseEntity.notFound().build();
        }
        if (queueItem.getStatus() != QueueItem.Status.PLAYING) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        String audioToken = jwtUtil.generateAudioToken(
                queueItemId, partyId, playerInstanceId, user.getId(), user.getRole().name());
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(audioToken);
    }

    @GetMapping("/audio")
    public ResponseEntity<Resource> getAudioFile(
            @RequestParam UUID queueItemId,
            @RequestParam UUID partyId,
            @RequestParam UUID playerInstanceId,
            @RequestParam(required = false) String token,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader) {
        JwtUtil.AudioTokenClaims claims = jwtUtil.validateAudioTokenClaims(token);
        if (claims == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (!claims.queueItemId().equals(queueItemId)
                || !claims.partyId().equals(partyId)
                || !claims.playerInstanceId().equals(playerInstanceId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (!playbackLeaseService.isActive(partyId, claims.hostUserId(), playerInstanceId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Party party = partyRepository.findById(partyId).orElse(null);
        QueueItem playingItem = queueItemRepository.findById(queueItemId).orElse(null);
        if (party == null || !Boolean.TRUE.equals(party.getIsActive())
                || !party.getHostUserId().equals(claims.hostUserId())
                || playingItem == null
                || !playingItem.getPartyId().equals(partyId)
                || playingItem.getStatus() != QueueItem.Status.PLAYING) {
            return ResponseEntity.status(HttpStatus.GONE).build();
        }

        Optional<String> filePath = playbackService.getTrackFilePath(queueItemId);
        if (filePath.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        try {
            Path path = Paths.get(filePath.get());
            File file = path.toFile();
            
            if (!file.exists() || !file.isFile()) {
                log.error("Audio file not found: {}", filePath.get());
                return ResponseEntity.notFound().build();
            }

            long fileLength = file.length();
            Resource resource = new FileSystemResource(file);
            
            // Determine content type based on file extension
            String contentType = Files.probeContentType(path);
            if (contentType == null || !contentType.startsWith("audio/")) {
                contentType = "audio/mpeg"; // Default to mp3
            }

            String contentDisposition = ContentDisposition.inline()
                    .filename(file.getName(), StandardCharsets.UTF_8)
                    .build()
                    .toString();

            if (rangeHeader != null) {
                List<HttpRange> ranges;
                try {
                    ranges = HttpRange.parseRanges(rangeHeader);
                } catch (IllegalArgumentException exception) {
                    return rangeNotSatisfiable(fileLength);
                }
                if (ranges.size() != 1) {
                    return rangeNotSatisfiable(fileLength);
                }

                HttpRange range = ranges.get(0);
                long start;
                long end;
                try {
                    start = range.getRangeStart(fileLength);
                    end = range.getRangeEnd(fileLength);
                } catch (IllegalArgumentException exception) {
                    return rangeNotSatisfiable(fileLength);
                }
                if (start < 0 || end < start || start >= fileLength || end >= fileLength) {
                    return rangeNotSatisfiable(fileLength);
                }

                long contentLength = end - start + 1;
                Resource rangedResource = rangeResource(path, start, contentLength);
                return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                        .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                        .contentType(MediaType.parseMediaType(contentType))
                        .header(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + fileLength)
                        .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(contentLength))
                        .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                        .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                        .body(rangedResource);
            }

            return ResponseEntity.ok()
                    .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(fileLength))
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                    .body(resource);
        } catch (Exception e) {
            log.error("Error serving audio file for queue item {}", queueItemId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private Party requireHost(UUID partyId, User user) {
        Party party = partyRepository.findById(partyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Party not found"));
        if (!Boolean.TRUE.equals(party.getIsActive())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Party is not active");
        }
        if (user == null || !party.getHostUserId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the party host can control playback");
        }
        return party;
    }

    private void requirePlayerLease(UUID partyId, UUID hostUserId, UUID playerInstanceId) {
        if (!playbackLeaseService.isActive(partyId, hostUserId, playerInstanceId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This player no longer owns the party playback lease");
        }
    }

    private ResponseEntity<Resource> rangeNotSatisfiable(long fileLength) {
        return ResponseEntity.<Resource>status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header(HttpHeaders.CONTENT_RANGE, "bytes */" + fileLength)
                .build();
    }

    private Resource rangeResource(Path path, long start, long contentLength) throws IOException {
        return new org.springframework.core.io.AbstractResource() {
            @Override
            public String getDescription() {
                return "Bounded audio resource: " + path + " [" + start + "-" + (start + contentLength - 1) + "]";
            }

            @Override
            public InputStream getInputStream() throws IOException {
                InputStream input = Files.newInputStream(path);
                long remaining = start;
                while (remaining > 0) {
                    long skipped = input.skip(remaining);
                    if (skipped == 0) {
                        if (input.read() == -1) {
                            input.close();
                            throw new EOFException("Range starts beyond the end of the audio file");
                        }
                        skipped = 1;
                    }
                    remaining -= skipped;
                }
                return new BoundedInputStream(input, contentLength);
            }

            @Override
            public long contentLength() throws IOException {
                return contentLength;
            }

            @Override
            public String getFilename() {
                return path.getFileName().toString();
            }
        };
    }

    private static final class BoundedInputStream extends FilterInputStream {
        private long remaining;

        private BoundedInputStream(InputStream input, long length) {
            super(input);
            this.remaining = length;
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int value = super.read();
            if (value != -1) {
                remaining--;
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (length == 0) {
                return 0;
            }
            if (remaining <= 0) {
                return -1;
            }
            int bytesRead = super.read(buffer, offset, (int) Math.min(length, remaining));
            if (bytesRead > 0) {
                remaining -= bytesRead;
            }
            return bytesRead;
        }
    }
}
