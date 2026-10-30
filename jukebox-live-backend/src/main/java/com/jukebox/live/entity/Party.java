package com.jukebox.live.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "parties")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Party {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 10)
    private String code;

    @Column(name = "host_user_id", nullable = false)
    private UUID hostUserId;

    @Column(name = "party_ip", nullable = false, length = 45)
    private String partyIp;

    @Column
    private Double latitude;

    @Column
    private Double longitude;

    @Column(name = "geofence_radius_meters", nullable = false)
    @Builder.Default
    private Double geofenceRadiusMeters = 100.0;

    @Column(name = "music_library_path", length = 500)
    private String musicLibraryPath;

    @Column(name = "spotify_device_id", length = 255)
    private String spotifyDeviceId;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "credits_valid_days", nullable = false)
    @Builder.Default
    private Integer creditsValidDays = 30;
}
