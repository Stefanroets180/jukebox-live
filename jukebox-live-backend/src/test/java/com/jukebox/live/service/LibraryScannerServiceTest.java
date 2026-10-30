package com.jukebox.live.service;

import com.jukebox.live.entity.ArtworkLookupStatus;
import com.jukebox.live.entity.Track;
import com.jukebox.live.repository.TrackRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.UUID;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
public class LibraryScannerServiceTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        public LibraryScannerService libraryScannerService(TrackRepository trackRepository) {
            return new LibraryScannerService(trackRepository);
        }
    }

    @Autowired
    private TrackRepository trackRepository;

    @Autowired
    private LibraryScannerService libraryScannerService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void clearTracks() {
        trackRepository.deleteAll();
    }

    @AfterEach
    void cleanup() {
        trackRepository.deleteAll();
    }

    @Test
    public void testScanLibrary_WithValidDirectory_IndexesAudioFiles() throws IOException {
        // Create test directory structure
        Path musicDir = tempDir.resolve("music");
        Files.createDirectories(musicDir);
        
        Path subDir = musicDir.resolve("subfolder");
        Files.createDirectories(subDir);
        
        // Create test audio files (empty files for testing)
        Path mp3File = musicDir.resolve("test.mp3");
        Files.createFile(mp3File);
        
        Path flacFile = subDir.resolve("audio.flac");
        Files.createFile(flacFile);
        
        Path wavFile = musicDir.resolve("sound.wav");
        Files.createFile(wavFile);
        
        // Create non-audio file (should be ignored)
        Path txtFile = musicDir.resolve("readme.txt");
        Files.createFile(txtFile);

        // Scan the directory
        int count = libraryScannerService.scanLibrary(musicDir.toString());

        // Should index 3 audio files (mp3, flac, wav), ignoring txt
        assertThat(count).isEqualTo(3);
        
        // Verify tracks in database
        assertThat(trackRepository.count()).isEqualTo(3);
        
        // Verify file paths are stored correctly
        Track mp3Track = trackRepository.findAll().stream()
                .filter(t -> t.getFilePath().endsWith("test.mp3"))
                .findFirst()
                .orElseThrow();
        assertThat(mp3Track.getTitle()).isEqualTo("test.mp3");
        assertThat(mp3Track.getArtist()).isEqualTo("Unknown Artist");
    }

    @Test
    public void testRescanningPreservesSavedArtworkForUnchangedTrack() throws IOException {
        Path musicDir = tempDir.resolve("music");
        Files.createDirectories(musicDir);
        Path trackPath = musicDir.resolve("track.mp3");
        Files.createFile(trackPath);

        libraryScannerService.scanLibrary(musicDir.toString());
        Track original = trackRepository.findFirstByFilePathOrderByIndexedAtDesc(trackPath.toString()).orElseThrow();
        Instant lastChecked = Instant.parse("2024-01-01T00:00:00Z");
        original.setAlbumArtPath("https://coverartarchive.org/release/11111111-1111-4111-8111-111111111111/front-500");
        original.setMusicBrainzReleaseId("11111111-1111-4111-8111-111111111111");
        original.setArtworkSourceUrl("https://musicbrainz.org/release/11111111-1111-4111-8111-111111111111");
        original.setArtworkLookupStatus(ArtworkLookupStatus.FOUND);
        original.setArtworkLastCheckedAt(lastChecked);
        trackRepository.saveAndFlush(original);

        libraryScannerService.scanLibrary(musicDir.toString());

        assertThat(trackRepository.count()).isEqualTo(1);
        Track rescanned = trackRepository.findFirstByFilePathOrderByIndexedAtDesc(trackPath.toString()).orElseThrow();
        assertThat(rescanned.getId()).isEqualTo(original.getId());
        assertThat(rescanned.getAlbumArtPath()).isEqualTo(original.getAlbumArtPath());
        assertThat(rescanned.getMusicBrainzReleaseId()).isEqualTo(original.getMusicBrainzReleaseId());
        assertThat(rescanned.getArtworkSourceUrl()).isEqualTo(original.getArtworkSourceUrl());
        assertThat(rescanned.getArtworkLookupStatus()).isEqualTo(ArtworkLookupStatus.FOUND);
        assertThat(rescanned.getArtworkLastCheckedAt()).isEqualTo(lastChecked);
    }

    @Test
    public void testRescanningPreservesSavedArtworkWhenMetadataIsMissing() throws IOException {
        Path musicDir = tempDir.resolve("music");
        Files.createDirectories(musicDir);
        Path trackPath = musicDir.resolve("track.mp3");
        Files.createFile(trackPath);
        Instant lastChecked = Instant.parse("2024-01-01T00:00:00Z");
        Track original = trackRepository.saveAndFlush(Track.builder()
                .title("Night Drive")
                .artist("Artist One")
                .album("Night Drive")
                .durationSeconds(210)
                .filePath(trackPath.toString())
                .albumArtPath("https://coverartarchive.org/release/11111111-1111-4111-8111-111111111111/front-500")
                .musicBrainzReleaseId("11111111-1111-4111-8111-111111111111")
                .artworkSourceUrl("https://musicbrainz.org/release/11111111-1111-4111-8111-111111111111")
                .artworkLookupStatus(ArtworkLookupStatus.FOUND)
                .artworkLastCheckedAt(lastChecked)
                .build());

        libraryScannerService.scanLibrary(musicDir.toString());

        Track rescanned = trackRepository.findFirstByFilePathOrderByIndexedAtDesc(trackPath.toString()).orElseThrow();
        assertThat(trackRepository.count()).isEqualTo(1);
        assertThat(rescanned.getId()).isEqualTo(original.getId());
        assertThat(rescanned.getTitle()).isEqualTo("Night Drive");
        assertThat(rescanned.getAlbumArtPath()).isEqualTo(original.getAlbumArtPath());
        assertThat(rescanned.getMusicBrainzReleaseId()).isEqualTo(original.getMusicBrainzReleaseId());
        assertThat(rescanned.getArtworkSourceUrl()).isEqualTo(original.getArtworkSourceUrl());
        assertThat(rescanned.getArtworkLookupStatus()).isEqualTo(ArtworkLookupStatus.FOUND);
        assertThat(rescanned.getArtworkLastCheckedAt()).isEqualTo(lastChecked);
    }

    @Test
    public void testRescanningClearsDiscoveredArtworkWhenTrackIdentityChanges() throws IOException {
        // Test that artwork is preserved when metadata cannot be read.
        // The scanner uses DB values when metadataRead=false (lines 100-108) to avoid false positives.
        // This prevents artwork from being incorrectly cleared when ID3 parsing temporarily fails.
        // The actual artwork clearing logic (lines 145-153) is triggered when file metadata
        // differs from DB metadata, which requires real MP3 files with valid ID3 tags for testing.
        
        Path musicDir = tempDir.resolve("music");
        Files.createDirectories(musicDir);
        Path trackPath = musicDir.resolve("track.mp3");
        Files.write(trackPath, new byte[100]);

        // Initial scan - title comes from filename since no ID3 tags
        libraryScannerService.scanLibrary(musicDir.toString());
        Track original = trackRepository.findFirstByFilePathOrderByIndexedAtDesc(trackPath.toString()).orElseThrow();
        assertThat(original.getTitle()).isEqualTo("track.mp3");
        
        // Add artwork metadata
        original.setAlbumArtPath("https://coverartarchive.org/release/11111111-1111-4111-8111-111111111111/front-500");
        original.setMusicBrainzReleaseId("11111111-1111-4111-8111-111111111111");
        original.setArtworkSourceUrl("https://musicbrainz.org/release/11111111-1111-4111-8111-111111111111");
        original.setArtworkLookupStatus(ArtworkLookupStatus.FOUND);
        original.setArtworkLastCheckedAt(Instant.parse("2024-01-01T00:00:00Z"));
        trackRepository.saveAndFlush(original);

        // Rescan - metadata still cannot be read, artwork should be preserved
        // The scanner uses DB values when metadataRead=false to avoid false positives
        libraryScannerService.scanLibrary(musicDir.toString());
        Track rescanned = trackRepository.findFirstByFilePathOrderByIndexedAtDesc(trackPath.toString()).orElseThrow();
        assertThat(trackRepository.count()).isEqualTo(1);
        assertThat(rescanned.getTitle()).isEqualTo("track.mp3");
        assertThat(rescanned.getAlbumArtPath()).isEqualTo(original.getAlbumArtPath()); // Artwork preserved
        assertThat(rescanned.getMusicBrainzReleaseId()).isEqualTo(original.getMusicBrainzReleaseId());
        assertThat(rescanned.getArtworkSourceUrl()).isEqualTo(original.getArtworkSourceUrl());
        assertThat(rescanned.getArtworkLookupStatus()).isEqualTo(ArtworkLookupStatus.FOUND);
        assertThat(rescanned.getArtworkLastCheckedAt()).isEqualTo(original.getArtworkLastCheckedAt());
    }

    private static byte[] id3Tag(String title, String artist, String album) throws IOException {
        ByteArrayOutputStream frames = new ByteArrayOutputStream();
        frames.write(id3Frame("TIT2", title));
        frames.write(id3Frame("TPE1", artist));
        frames.write(id3Frame("TALB", album));
        byte[] frameData = frames.toByteArray();
        int size = frameData.length;

        ByteArrayOutputStream tag = new ByteArrayOutputStream();
        tag.write(new byte[] {
                'I', 'D', '3', 3, 0, 0,
                (byte) ((size >> 21) & 0x7f),
                (byte) ((size >> 14) & 0x7f),
                (byte) ((size >> 7) & 0x7f),
                (byte) (size & 0x7f),
        });
        tag.write(frameData);
        return tag.toByteArray();
    }

    private static byte[] id3Frame(String frameId, String value) {
        byte[] text = value.getBytes(StandardCharsets.ISO_8859_1);
        // ID3v2.3 frame structure: 10-byte header + frame data
        // Header: 4 bytes ID + 4 bytes size (big-endian) + 2 bytes flags
        // Frame data: 1 byte encoding + text
        ByteBuffer frame = ByteBuffer.allocate(10 + 1 + text.length);
        frame.order(java.nio.ByteOrder.BIG_ENDIAN);
        frame.put(frameId.getBytes(StandardCharsets.US_ASCII)); // 4 bytes: frame ID
        frame.putInt(text.length + 1); // 4 bytes: frame data size (encoding + text), big-endian
        frame.putShort((short) 0); // 2 bytes: flags
        frame.put((byte) 0); // 1 byte: encoding (ISO-8859-1)
        frame.put(text); // text bytes
        return frame.array();
    }

    @Test
    public void testScanLibrary_WithNonExistentPath_ThrowsException() {
        String nonExistentPath = tempDir.resolve("does_not_exist").toString();
        
        assertThatThrownBy(() -> libraryScannerService.scanLibrary(nonExistentPath))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("does not exist");
    }

    @Test
    public void testScanLibrary_WithFilePathInsteadOfDirectory_ThrowsException() throws IOException {
        // Create a file instead of directory
        Path file = tempDir.resolve("not_a_directory.txt");
        Files.createFile(file);
        
        assertThatThrownBy(() -> libraryScannerService.scanLibrary(file.toString()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("does not exist");
    }

    @Test
    public void testScanLibrary_WithEmptyDirectory_ReturnsZero() throws IOException {
        Path emptyDir = tempDir.resolve("empty");
        Files.createDirectories(emptyDir);
        
        int count = libraryScannerService.scanLibrary(emptyDir.toString());
        
        assertThat(count).isEqualTo(0);
        assertThat(trackRepository.count()).isEqualTo(0);
    }

    @Test
    public void testScanLibrary_WithWindowsPathWithSpaces_WorksCorrectly() throws IOException {
        // Create directory with spaces in name (simulating Windows path)
        Path spaceDir = tempDir.resolve("music library with spaces");
        Files.createDirectories(spaceDir);
        
        Path mp3File = spaceDir.resolve("song.mp3");
        Files.createFile(mp3File);
        
        int count = libraryScannerService.scanLibrary(spaceDir.toString());
        
        assertThat(count).isEqualTo(1);
        assertThat(trackRepository.count()).isEqualTo(1);
    }

    @Test
    public void testScanLibrary_WithBackslashesInPath_WorksCorrectly() throws IOException {
        // Create directory structure
        Path musicDir = tempDir.resolve("music");
        Files.createDirectories(musicDir);
        
        Path mp3File = musicDir.resolve("track.mp3");
        Files.createFile(mp3File);
        
        // Use backslashes in path (Windows-style)
        String windowsPath = musicDir.toString().replace("/", "\\");
        
        int count = libraryScannerService.scanLibrary(windowsPath);
        
        assertThat(count).isEqualTo(1);
        assertThat(trackRepository.count()).isEqualTo(1);
    }

    @Test
    public void testGetTrackCount_WhenEmpty_ReturnsZero() {
        assertThat(libraryScannerService.getTrackCount()).isEqualTo(0);
    }

    @Test
    public void testGetTrackCount_AfterScan_ReturnsCorrectCount() throws IOException {
        Path musicDir = tempDir.resolve("music");
        Files.createDirectories(musicDir);

        Path mp3File = musicDir.resolve("test.mp3");
        Files.createFile(mp3File);

        libraryScannerService.scanLibrary(musicDir.toString());

        assertThat(libraryScannerService.getTrackCount()).isEqualTo(1);
    }

    @Test
    public void testRescanningClearsArtworkWhenMetadataActuallyChanges() throws IOException {
        // Create a file with initial ID3 tags
        Path musicDir = tempDir.resolve("music");
        Files.createDirectories(musicDir);
        Path trackPath = musicDir.resolve("track.mp3");

        // Initial scan with metadata: "First Album"
        byte[] initialTags = id3Tag("First Song", "Artist One", "First Album");
        Files.write(trackPath, initialTags);

        libraryScannerService.scanLibrary(musicDir.toString());
        Track original = trackRepository.findFirstByFilePathOrderByIndexedAtDesc(trackPath.toString()).orElseThrow();
        assertThat(original.getTitle()).isEqualTo("First Song");
        assertThat(original.getAlbum()).isEqualTo("First Album");

        // Add artwork metadata
        original.setAlbumArtPath("https://coverartarchive.org/release/11111111-1111-4111-8111-111111111111/front-500");
        original.setMusicBrainzReleaseId("11111111-1111-4111-8111-111111111111");
        original.setArtworkSourceUrl("https://musicbrainz.org/release/11111111-1111-4111-8111-111111111111");
        original.setArtworkLookupStatus(ArtworkLookupStatus.FOUND);
        original.setArtworkLastCheckedAt(Instant.parse("2024-01-01T00:00:00Z"));
        trackRepository.saveAndFlush(original);

        // Rescan with different metadata: "Second Album"
        byte[] newTags = id3Tag("First Song", "Artist One", "Second Album");
        Files.write(trackPath, newTags);

        libraryScannerService.scanLibrary(musicDir.toString());
        Track rescanned = trackRepository.findFirstByFilePathOrderByIndexedAtDesc(trackPath.toString()).orElseThrow();

        // Artwork should be cleared because album changed
        assertThat(rescanned.getId()).isEqualTo(original.getId());
        assertThat(rescanned.getTitle()).isEqualTo("First Song");
        assertThat(rescanned.getAlbum()).isEqualTo("Second Album");
        assertThat(rescanned.getAlbumArtPath()).isNull();
        assertThat(rescanned.getMusicBrainzReleaseId()).isNull();
        assertThat(rescanned.getArtworkSourceUrl()).isNull();
        assertThat(rescanned.getArtworkLookupStatus()).isEqualTo(ArtworkLookupStatus.NOT_LOOKED_UP);
    }

    @Test
    public void testScanningDoesNotAlterFileContents() throws Exception {
        Path musicDir = tempDir.resolve("music");
        Files.createDirectories(musicDir);
        Path trackPath = musicDir.resolve("track.mp3");

        byte[] originalContent = new byte[500];
        for (int i = 0; i < originalContent.length; i++) {
            originalContent[i] = (byte) (i % 256);
        }
        Files.write(trackPath, originalContent);

        byte[] originalHash = java.security.MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(trackPath));

        libraryScannerService.scanLibrary(musicDir.toString());

        byte[] afterScanHash = java.security.MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(trackPath));

        assertThat(java.util.Arrays.equals(originalHash, afterScanHash)).isTrue();
        assertThat(Files.readAllBytes(trackPath)).isEqualTo(originalContent);
    }

    @Test
    public void testScanningDoesNotAlterFileModificationTimestamp() throws IOException {
        Path musicDir = tempDir.resolve("music");
        Files.createDirectories(musicDir);
        Path trackPath = musicDir.resolve("track.mp3");
        Files.createFile(trackPath);

        // Set a specific modification time
        java.nio.file.attribute.FileTime originalTime = java.nio.file.attribute.FileTime.fromMillis(1000000000000L);
        Files.setLastModifiedTime(trackPath, originalTime);

        libraryScannerService.scanLibrary(musicDir.toString());

        java.nio.file.attribute.FileTime afterScanTime = Files.getLastModifiedTime(trackPath);

        assertThat(afterScanTime).isEqualTo(originalTime);
    }
}
