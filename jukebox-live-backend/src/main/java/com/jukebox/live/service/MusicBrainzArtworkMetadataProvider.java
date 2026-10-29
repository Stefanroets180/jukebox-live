package com.jukebox.live.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.jukebox.live.dto.ArtworkCandidateDto;
import com.jukebox.live.entity.Track;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

@Component
public class MusicBrainzArtworkMetadataProvider implements ArtworkMetadataProvider {
    private static final int MAX_RECORDINGS = 5;
    private static final int MAX_RELEASE_CANDIDATES = 8;
    private static final long MUSICBRAINZ_MIN_INTERVAL_NANOS = 1_050_000_000L;

    private final RestClient musicBrainz;
    private final RestClient coverArtArchive;
    private long lastMusicBrainzRequestNanos;

    public MusicBrainzArtworkMetadataProvider(
            @Value("${jukebox.artwork.musicbrainz-user-agent:JukeBoxLive/1.0 (https://jukebox.live)}") String userAgent) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(3_000);
        requestFactory.setReadTimeout(5_000);

        this.musicBrainz = RestClient.builder()
                .baseUrl("https://musicbrainz.org/ws/2")
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.USER_AGENT, userAgent)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.coverArtArchive = RestClient.builder()
                .baseUrl("https://coverartarchive.org")
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.USER_AGENT, userAgent)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public List<ArtworkCandidateDto> findCandidates(Track track) {
        if (track.getTitle() == null || track.getTitle().isBlank()
                || track.getArtist() == null || track.getArtist().isBlank()) {
            return List.of();
        }

        String query = "recording:" + quote(track.getTitle()) + " AND artist:" + quote(track.getArtist());
        JsonNode search = musicBrainzGet(() -> musicBrainz.get()
                .uri(uriBuilder -> uriBuilder.path("recording")
                        .queryParam("query", query)
                        .queryParam("fmt", "json")
                        .queryParam("limit", MAX_RECORDINGS)
                        .build())
                .retrieve()
                .body(JsonNode.class));

        JsonNode recordings = search == null ? null : search.path("recordings");
        if (recordings == null || !recordings.isArray()) {
            return List.of();
        }

        Map<String, ArtworkCandidateDto> releases = new LinkedHashMap<>();
        List<JsonNode> recordingHits = new ArrayList<>();
        recordings.forEach(recordingHits::add);
        recordingHits.sort(Comparator.comparingInt((JsonNode node) -> node.path("score").asInt(0)).reversed());

        int lookedUpRecordings = 0;
        for (JsonNode recording : recordingHits) {
            String recordingId = recording.path("id").asText("");
            String recordingTitle = recording.path("title").asText("");
            String artist = artistCredit(recording.path("artist-credit"));
            int score = recording.path("score").asInt(0);
            if (!isExact(recordingTitle, track.getTitle()) || !isExact(artist, track.getArtist()) || score < 85) {
                continue;
            }

            JsonNode releaseList = recording.path("releases");
            if (!releaseList.isArray() || releaseList.isEmpty()) {
                if (recordingId.isBlank() || lookedUpRecordings >= 3) {
                    continue;
                }
                lookedUpRecordings++;
                JsonNode detail = musicBrainzGet(() -> musicBrainz.get()
                        .uri(uriBuilder -> uriBuilder.path("recording/{id}")
                                .queryParam("inc", "releases")
                                .queryParam("fmt", "json")
                                .build(recordingId))
                        .retrieve()
                        .body(JsonNode.class));
                releaseList = detail == null ? null : detail.path("releases");
            }

            if (releaseList == null || !releaseList.isArray()) {
                continue;
            }

            for (JsonNode release : releaseList) {
                String releaseId = release.path("id").asText("");
                if (!isUuid(releaseId) || releases.containsKey(releaseId)) {
                    continue;
                }
                releases.put(releaseId, ArtworkCandidateDto.builder()
                        .recordingId(recordingId)
                        .recordingTitle(recordingTitle)
                        .releaseId(releaseId)
                        .releaseTitle(release.path("title").asText(""))
                        .artist(artist)
                        .releaseDate(release.path("date").asText(""))
                        .country(release.path("country").asText(""))
                        .score(score)
                        .build());
            }
        }

        return releases.values().stream()
                .sorted(Comparator
                        .comparing((ArtworkCandidateDto candidate) -> albumMatch(track, candidate)).reversed()
                        .thenComparing(ArtworkCandidateDto::getScore, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(MAX_RELEASE_CANDIDATES)
                .map(candidate -> getFrontCoverUrl(candidate.getReleaseId())
                        .map(url -> ArtworkCandidateDto.builder()
                                .recordingId(candidate.getRecordingId())
                                .recordingTitle(candidate.getRecordingTitle())
                                .releaseId(candidate.getReleaseId())
                                .releaseTitle(candidate.getReleaseTitle())
                                .artist(candidate.getArtist())
                                .releaseDate(candidate.getReleaseDate())
                                .country(candidate.getCountry())
                                .score(candidate.getScore())
                                .coverArtUrl(url)
                                .sourceUrl(musicBrainzReleaseUrl(candidate.getReleaseId()))
                                .build())
                        .orElse(null))
                .filter(candidate -> candidate != null)
                .toList();
    }

    @Override
    public Optional<String> getFrontCoverUrl(String releaseId) {
        if (!isUuid(releaseId)) {
            return Optional.empty();
        }

        try {
            JsonNode response = coverArtArchive.get()
                    .uri("/release/{id}", releaseId)
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode images = response == null ? null : response.path("images");
            if (images == null || !images.isArray()) {
                return Optional.empty();
            }
            boolean hasApprovedFront = false;
            for (JsonNode image : images) {
                if (image.path("front").asBoolean(false) && image.path("approved").asBoolean(false)) {
                    hasApprovedFront = true;
                    break;
                }
            }
            return hasApprovedFront
                    ? Optional.of("https://coverartarchive.org/release/" + releaseId + "/front-500")
                    : Optional.empty();
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) {
                return Optional.empty();
            }
            throw new ArtworkProviderException("Cover Art Archive is temporarily unavailable.", exception);
        } catch (Exception exception) {
            throw new ArtworkProviderException("Cover Art Archive could not be reached.", exception);
        }
    }

    private JsonNode musicBrainzGet(Supplier<JsonNode> request) {
        synchronized (this) {
            long waitNanos = MUSICBRAINZ_MIN_INTERVAL_NANOS - (System.nanoTime() - lastMusicBrainzRequestNanos);
            if (lastMusicBrainzRequestNanos != 0 && waitNanos > 0) {
                try {
                    long millis = waitNanos / 1_000_000L;
                    int nanos = (int) (waitNanos % 1_000_000L);
                    Thread.sleep(millis, nanos);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new ArtworkProviderException("Artwork lookup was interrupted.", exception);
                }
            }
            lastMusicBrainzRequestNanos = System.nanoTime();
            try {
                return request.get();
            } catch (RestClientResponseException exception) {
                throw new ArtworkProviderException("MusicBrainz is temporarily unavailable.", exception);
            } catch (Exception exception) {
                throw new ArtworkProviderException("MusicBrainz could not be reached.", exception);
            }
        }
    }

    private static String artistCredit(JsonNode artistCredit) {
        if (artistCredit == null || !artistCredit.isArray()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (JsonNode credit : artistCredit) {
            String name = credit.path("name").asText("");
            if (name.isBlank()) {
                name = credit.path("artist").path("name").asText("");
            }
            builder.append(name).append(credit.path("joinphrase").asText(""));
        }
        return builder.toString().trim();
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static boolean isExact(String left, String right) {
        return normalize(left).equals(normalize(right));
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.strip().toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim();
    }

    private static boolean albumMatch(Track track, ArtworkCandidateDto candidate) {
        return track.getAlbum() != null && !track.getAlbum().isBlank()
                && isExact(track.getAlbum(), candidate.getReleaseTitle());
    }

    private static boolean isUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    private static String musicBrainzReleaseUrl(String releaseId) {
        return "https://musicbrainz.org/release/" + releaseId;
    }
}
