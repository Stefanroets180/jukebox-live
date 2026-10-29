package com.jukebox.live.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tracks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Track {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, length = 255)
    private String artist;

    @Column(length = 255)
    private String album;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Column(name = "album_art_path", length = 500)
    private String albumArtPath;

    @Column(name = "musicbrainz_release_id", length = 36)
    private String musicBrainzReleaseId;

    @Enumerated(EnumType.STRING)
    @Column(name = "artwork_lookup_status", nullable = false, length = 20)
    @Builder.Default
    private ArtworkLookupStatus artworkLookupStatus = ArtworkLookupStatus.NOT_LOOKED_UP;

    @Column(name = "artwork_last_checked_at")
    private Instant artworkLastCheckedAt;

    @Column(name = "artwork_candidates_json", columnDefinition = "TEXT")
    private String artworkCandidatesJson;

    @Column(name = "artwork_source_url", length = 500)
    private String artworkSourceUrl;

    @Column(length = 100)
    private String genre;

    @Column(name = "file_size")
    private Long fileSize;

    @CreationTimestamp
    @Column(name = "indexed_at", nullable = false, updatable = false)
    private Instant indexedAt;
}
