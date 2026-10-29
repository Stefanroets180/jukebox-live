package com.jukebox.live.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtUtil {

    private static final long MAX_AUDIO_TOKEN_EXPIRATION_MS = 300000; // 5 minutes maximum

    private final SecretKey key;
    private final long expirationMs;
    private final long audioTokenExpirationMs;

    public JwtUtil(
            String jwtSecret,
            @Value("${jukebox.jwt.expiration-ms:86400000}") long expirationMs,
            @Value("${jukebox.jwt.audio-token-expiration-ms:300000}") long audioTokenExpirationMs) {
        this.key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
        
        // Validate audio token expiration configuration
        if (audioTokenExpirationMs <= 0) {
            throw new IllegalStateException(
                "jukebox.jwt.audio-token-expiration-ms must be positive. Current value: " + audioTokenExpirationMs
            );
        }
        if (audioTokenExpirationMs > MAX_AUDIO_TOKEN_EXPIRATION_MS) {
            throw new IllegalStateException(
                "jukebox.jwt.audio-token-expiration-ms must not exceed " + MAX_AUDIO_TOKEN_EXPIRATION_MS + 
                "ms (5 minutes). Current value: " + audioTokenExpirationMs
            );
        }
        this.audioTokenExpirationMs = audioTokenExpirationMs;
    }

    public String generateToken(UUID userId, String username, String role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);
        return Jwts.builder()
                .subject(userId.toString())
                .claim("username", username)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    /**
     * Generate a short-lived token for audio playback access.
     * Scopes the token to a specific queue item and expires in 5 minutes by default.
     */
    public String generateAudioToken(UUID queueItemId, UUID partyId, UUID playerInstanceId, UUID hostUserId, String role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + audioTokenExpirationMs);
        return Jwts.builder()
                .subject(queueItemId.toString())
                .claim("type", "audio")
                .claim("role", role)
                .claim("partyId", partyId.toString())
                .claim("playerInstanceId", playerInstanceId.toString())
                .claim("hostUserId", hostUserId.toString())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public UUID getUserId(String token) {
        return UUID.fromString(parseToken(token).getSubject());
    }

    public String getUsername(String token) {
        return parseToken(token).get("username", String.class);
    }

    public String getRole(String token) {
        return parseToken(token).get("role", String.class);
    }

    /**
     * Validate an audio token and return the queue item ID it authorizes.
     * Returns null if token is invalid, expired, or not an audio token.
     */
    public UUID validateAudioToken(String token) {
        AudioTokenClaims claims = validateAudioTokenClaims(token);
        return claims == null ? null : claims.queueItemId();
    }

    public AudioTokenClaims validateAudioTokenClaims(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            Claims claims = parseToken(token);
            if (!"audio".equals(claims.get("type", String.class))
                    || !"ADMIN".equals(claims.get("role", String.class))) {
                return null;
            }
            return new AudioTokenClaims(
                    UUID.fromString(claims.getSubject()),
                    UUID.fromString(claims.get("partyId", String.class)),
                    UUID.fromString(claims.get("playerInstanceId", String.class)),
                    UUID.fromString(claims.get("hostUserId", String.class)));
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    public record AudioTokenClaims(UUID queueItemId, UUID partyId, UUID playerInstanceId, UUID hostUserId) {}
}
