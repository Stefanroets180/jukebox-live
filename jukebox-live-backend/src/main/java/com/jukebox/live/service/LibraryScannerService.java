package com.jukebox.live.service;

import com.jukebox.live.entity.ArtworkLookupStatus;
import com.jukebox.live.entity.Track;
import com.jukebox.live.repository.TrackRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.Parser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.xml.sax.ContentHandler;
import org.xml.sax.helpers.DefaultHandler;

import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class LibraryScannerService {

    private final TrackRepository trackRepository;

    @Transactional
    public int scanLibrary(String musicPath) {
        Path root = Paths.get(musicPath);
        if (!Files.exists(root) || !Files.isDirectory(root)) {
            throw new RuntimeException("Music path does not exist: " + musicPath);
        }

        int count = 0;
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path filePath : paths.filter(Files::isRegularFile).toList()) {
                if (isAudioFile(filePath)) {
                    try {
                        indexTrack(filePath);
                        count++;
                    } catch (Exception e) {
                        log.warn("Failed to index: {}", filePath, e);
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to scan library", e);
        }
        return count;
    }

    private boolean isAudioFile(Path path) {
        String name = path.toString().toLowerCase();
        return name.endsWith(".mp3") || name.endsWith(".m4a") || name.endsWith(".flac") || name.endsWith(".wav");
    }

    private void indexTrack(Path filePath) throws Exception {
        String title = null;
        String artist = null;
        String album = null;
        String genre = null;
        Integer duration = null;
        boolean metadataRead = false;

        try {
            Parser parser = new AutoDetectParser();
            Metadata metadata = new Metadata();
            ContentHandler handler = new DefaultHandler();
            ParseContext context = new ParseContext();

            try (FileInputStream input = new FileInputStream(filePath.toFile())) {
                parser.parse(input, handler, metadata, context);
            }
            metadataRead = true;

            title = firstMetadata(metadata, "title", "dc:title");
            artist = firstMetadata(metadata, "xmpDM:artist", "artist", "dc:creator");
            album = firstMetadata(metadata, "xmpDM:album", "album");
            genre = firstMetadata(metadata, "xmpDM:genre", "genre");

            String durationStr = metadata.get("xmpDM:duration");
            if (durationStr != null && !durationStr.isBlank()) {
                try {
                    duration = (int) Double.parseDouble(durationStr);
                } catch (NumberFormatException e) {
                    log.debug("Could not parse duration: {}", durationStr);
                }
            }
        } catch (Exception e) {
            log.debug("Could not read metadata for {}, using filename", filePath);
        }

        Track track = trackRepository.findFirstByFilePathOrderByIndexedAtDesc(filePath.toString()).orElse(null);
        if (track != null) {
            if (!metadataRead) {
                // When metadata cannot be read, use DB values to avoid false positives
                // Artwork clearing should only happen when file metadata actually changes,
                // not when ID3 parsing temporarily fails
                title = track.getTitle();
                artist = track.getArtist();
                album = track.getAlbum();
                genre = track.getGenre();
                duration = track.getDurationSeconds();
            } else {
                title = preferNonBlank(title, track.getTitle());
                artist = preferNonBlank(artist, track.getArtist());
                album = preferNonBlank(album, track.getAlbum());
                genre = preferNonBlank(genre, track.getGenre());
                if (duration == null) {
                    duration = track.getDurationSeconds();
                }
            }
        }

        if (title == null || title.isBlank()) {
            title = filePath.getFileName().toString();
        }
        if (artist == null || artist.isBlank()) {
            artist = "Unknown Artist";
        }
        if (album != null && album.isBlank()) {
            album = null;
        }
        if (genre != null && genre.isBlank()) {
            genre = null;
        }
        if (duration == null) {
            duration = 0;
        }

        boolean artworkIdentityChanged = track != null
                && (!sameMetadataValue(track.getTitle(), title)
                        || !sameMetadataValue(track.getArtist(), artist)
                        || !sameMetadataValue(track.getAlbum(), album));

        if (track == null) {
            Track sameAlbum = album == null ? null : trackRepository
                    .findFirstByArtistIgnoreCaseAndAlbumIgnoreCaseAndArtworkCacheKeyIsNotNull(artist, album)
                    .orElse(null);
            track = Track.builder()
                    .id(UUID.randomUUID())
                    .filePath(filePath.toString())
                    .albumArtPath(sameAlbum == null ? null : sameAlbum.getAlbumArtPath())
                    .artworkCacheKey(sameAlbum == null ? null : sameAlbum.getArtworkCacheKey())
                    .musicBrainzReleaseId(sameAlbum == null ? null : sameAlbum.getMusicBrainzReleaseId())
                    .artworkSourceUrl(sameAlbum == null ? null : sameAlbum.getArtworkSourceUrl())
                    .artworkLookupStatus(sameAlbum == null ? ArtworkLookupStatus.NOT_LOOKED_UP : sameAlbum.getArtworkLookupStatus())
                    .build();
        } else if (artworkIdentityChanged) {
            if (track.getMusicBrainzReleaseId() != null || track.getArtworkSourceUrl() != null) {
                track.setAlbumArtPath(null);
            }
            track.setMusicBrainzReleaseId(null);
            track.setArtworkCacheKey(null);
            track.setArtworkSourceUrl(null);
            track.setArtworkLookupStatus(ArtworkLookupStatus.NOT_LOOKED_UP);
            track.setArtworkLastCheckedAt(null);
            track.setArtworkCandidatesJson(null);
        }

        track.setTitle(title);
        track.setArtist(artist);
        track.setAlbum(album);
        track.setDurationSeconds(duration);
        track.setFilePath(filePath.toString());
        track.setGenre(genre);
        track.setFileSize(Files.size(filePath));
        trackRepository.save(track);
    }

    private static String firstMetadata(Metadata metadata, String... keys) {
        for (String key : keys) {
            String value = metadata.get(key);
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    private static String preferNonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static boolean sameMetadataValue(String left, String right) {
        return normalizeMetadataValue(left).equals(normalizeMetadataValue(right));
    }

    private static String normalizeMetadataValue(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }

    public long getTrackCount() {
        return trackRepository.count();
    }
}
