package com.jukebox.live.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "party_guests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@IdClass(PartyGuest.PartyGuestId.class)
public class PartyGuest {

    @Id
    @Column(name = "party_id")
    private UUID partyId;

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "joined_ip", nullable = false, length = 45)
    private String joinedIp;

    @Column(name = "joined_lat")
    private Double joinedLat;

    @Column(name = "joined_lng")
    private Double joinedLng;

    @CreationTimestamp
    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PartyGuestId implements java.io.Serializable {
        private UUID partyId;
        private UUID userId;
    }
}
