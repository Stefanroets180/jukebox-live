package com.jukebox.live.service;

import com.jukebox.live.entity.Track;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

@Service
public class ArtworkCacheService {
    private final Path cacheRoot;
    private final RestClient client = RestClient.builder().build();

    public ArtworkCacheService(@Value("${jukebox.artwork.cache-path:${user.home}/.jukebox-live/artwork-cache}") String cachePath) {
        this.cacheRoot = Path.of(cachePath).toAbsolutePath().normalize();
    }

    public String identityFor(Track track) {
        if (track.getMusicBrainzReleaseId() != null && !track.getMusicBrainzReleaseId().isBlank()) {
            return "release-" + sha256(track.getMusicBrainzReleaseId().strip().toLowerCase(Locale.ROOT));
        }
        String albumIdentity = normalize(track.getArtist()) + "\u001f" + normalize(track.getAlbum());
        if (track.getAlbum() == null || track.getAlbum().isBlank()) {
            albumIdentity += "\u001f" + normalize(track.getTitle());
        }
        return "metadata-" + sha256(albumIdentity);
    }

    public CachedArtwork cache(String identity, String remoteUrl) {
        if (remoteUrl == null || remoteUrl.isBlank()) return null;
        try {
            Files.createDirectories(cacheRoot);
            Path target = cacheRoot.resolve(identity + ".jpg").normalize();
            if (target.startsWith(cacheRoot) && Files.isRegularFile(target) && Files.size(target) > 0) {
                return new CachedArtwork(identity, target, "/api/library/artwork/cache/" + identity, MediaType.IMAGE_JPEG_VALUE);
            }
            Path temporary = Files.createTempFile(cacheRoot, identity + "-", ".tmp");
            try (InputStream input = client.get().uri(remoteUrl).retrieve().body(InputStream.class)) {
                if (input == null) throw new IOException("Artwork response was empty");
                Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
                if (Files.size(temporary) == 0) throw new IOException("Artwork response was empty");
                try {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
            return new CachedArtwork(identity, target, "/api/library/artwork/cache/" + identity, MediaType.IMAGE_JPEG_VALUE);
        } catch (Exception ignored) {
            return existing(identity);
        }
    }

    public CachedArtwork existing(String identity) {
        try {
            Path target = cacheRoot.resolve(identity + ".jpg").normalize();
            if (target.startsWith(cacheRoot) && Files.isRegularFile(target) && Files.size(target) > 0) {
                return new CachedArtwork(identity, target, "/api/library/artwork/cache/" + identity, MediaType.IMAGE_JPEG_VALUE);
            }
        } catch (IOException ignored) {
        }
        return null;
    }

    public Resource resource(String identity) {
        CachedArtwork artwork = existing(identity);
        if (artwork == null) return null;
        try { return new UrlResource(artwork.path().toUri()); }
        catch (IOException ignored) { return null; }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    public record CachedArtwork(String identity, Path path, String localUrl, String contentType) {}
}
