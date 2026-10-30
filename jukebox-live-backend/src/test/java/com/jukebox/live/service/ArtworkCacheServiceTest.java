package com.jukebox.live.service;

import com.jukebox.live.entity.Track;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
public class ArtworkCacheServiceTest {

    @TempDir
    Path tempDir;

    private ArtworkCacheService artworkCacheService;

    @BeforeEach
    void setUp() {
        artworkCacheService = new ArtworkCacheService(tempDir.toString());
    }

    @Test
    public void testIdentityFor_UsesMusicBrainzReleaseId() {
        Track track = Track.builder()
                .id(UUID.randomUUID())
                .title("Test Song")
                .artist("Test Artist")
                .album("Test Album")
                .musicBrainzReleaseId("11111111-1111-4111-8111-111111111111")
                .build();

        String identity = artworkCacheService.identityFor(track);

        assertThat(identity).startsWith("release-");
        assertThat(identity.length()).isGreaterThan(20); // SHA-256 hex is 64 chars + prefix
    }

    @Test
    public void testIdentityFor_UsesMetadataWhenNoMusicBrainzId() {
        Track track = Track.builder()
                .id(UUID.randomUUID())
                .title("Test Song")
                .artist("Test Artist")
                .album("Test Album")
                .musicBrainzReleaseId(null)
                .build();

        String identity = artworkCacheService.identityFor(track);

        assertThat(identity).startsWith("metadata-");
        assertThat(identity.length()).isGreaterThan(20); // SHA-256 hex is 64 chars + prefix
    }

    @Test
    public void testIdentityFor_UsesTitleWhenAlbumMissing() {
        Track track = Track.builder()
                .id(UUID.randomUUID())
                .title("Test Song")
                .artist("Test Artist")
                .album(null)
                .musicBrainzReleaseId(null)
                .build();

        String identity = artworkCacheService.identityFor(track);

        assertThat(identity).startsWith("metadata-");
        assertThat(identity.length()).isGreaterThan(20);
    }

    @Test
    public void testIdentityFor_DifferentMetadataProducesDifferentIdentities() {
        Track track1 = Track.builder()
                .id(UUID.randomUUID())
                .title("Song One")
                .artist("Artist One")
                .album("Album One")
                .musicBrainzReleaseId(null)
                .build();

        Track track2 = Track.builder()
                .id(UUID.randomUUID())
                .title("Song Two")
                .artist("Artist Two")
                .album("Album Two")
                .musicBrainzReleaseId(null)
                .build();

        String identity1 = artworkCacheService.identityFor(track1);
        String identity2 = artworkCacheService.identityFor(track2);

        assertThat(identity1).isNotEqualTo(identity2);
    }

    @Test
    public void testIdentityFor_SameMetadataProducesSameIdentity() {
        Track track1 = Track.builder()
                .id(UUID.randomUUID())
                .title("Test Song")
                .artist("Test Artist")
                .album("Test Album")
                .musicBrainzReleaseId(null)
                .build();

        Track track2 = Track.builder()
                .id(UUID.randomUUID())
                .title("Test Song")
                .artist("Test Artist")
                .album("Test Album")
                .musicBrainzReleaseId(null)
                .build();

        String identity1 = artworkCacheService.identityFor(track1);
        String identity2 = artworkCacheService.identityFor(track2);

        assertThat(identity1).isEqualTo(identity2);
    }

    @Test
    public void testExisting_ReturnsNullWhenCacheDoesNotExist() {
        String identity = "metadata-" + "a".repeat(64);
        ArtworkCacheService.CachedArtwork artwork = artworkCacheService.existing(identity);

        assertThat(artwork).isNull();
    }

    @Test
    public void testExisting_ReturnsArtworkWhenCacheExists() throws Exception {
        String identity = "metadata-" + "a".repeat(64);
        Path cacheFile = tempDir.resolve(identity + ".jpg");
        Files.createFile(cacheFile);
        Files.write(cacheFile, new byte[100]);

        ArtworkCacheService.CachedArtwork artwork = artworkCacheService.existing(identity);

        assertThat(artwork).isNotNull();
        assertThat(artwork.identity()).isEqualTo(identity);
        assertThat(artwork.path()).isEqualTo(cacheFile);
        assertThat(artwork.localUrl()).isEqualTo("/api/library/artwork/cache/" + identity);
    }

    @Test
    public void testCache_DoesNotDownloadWhenFileAlreadyExists() throws Exception {
        String identity = "metadata-" + "a".repeat(64);
        Path cacheFile = tempDir.resolve(identity + ".jpg");
        Files.createFile(cacheFile);
        Files.write(cacheFile, new byte[100]);

        // Mock URL that would fail if actually called
        String fakeUrl = "http://fake-url-that-does-not-exist/artwork.jpg";
        ArtworkCacheService.CachedArtwork artwork = artworkCacheService.cache(identity, fakeUrl);

        assertThat(artwork).isNotNull();
        assertThat(artwork.identity()).isEqualTo(identity);
        // File should still have original content (not overwritten)
        assertThat(Files.size(cacheFile)).isEqualTo(100);
    }

    @Test
    public void testCache_ReturnsExistingWhenDownloadFails() throws Exception {
        String identity = "metadata-" + "a".repeat(64);
        Path cacheFile = tempDir.resolve(identity + ".jpg");
        Files.createFile(cacheFile);
        Files.write(cacheFile, new byte[100]);

        // Invalid URL that will fail
        String invalidUrl = "http://invalid";
        ArtworkCacheService.CachedArtwork artwork = artworkCacheService.cache(identity, invalidUrl);

        assertThat(artwork).isNotNull();
        assertThat(artwork.identity()).isEqualTo(identity);
        // Should return existing artwork even though download failed
        assertThat(Files.size(cacheFile)).isEqualTo(100);
    }
}
