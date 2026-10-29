package com.jukebox.live.repository;

import com.jukebox.live.entity.Track;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TrackRepository extends JpaRepository<Track, UUID> {

    Optional<Track> findFirstByFilePathOrderByIndexedAtDesc(String filePath);

    @Query("SELECT t FROM Track t WHERE " +
           "LOWER(t.title) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(t.artist) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(t.album) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<Track> search(@Param("query") String query);

    @Query(value = "SELECT t FROM Track t WHERE t.albumArtPath IS NULL OR TRIM(t.albumArtPath) = '' ORDER BY LOWER(t.artist), LOWER(t.title)",
           countQuery = "SELECT COUNT(t) FROM Track t WHERE t.albumArtPath IS NULL OR TRIM(t.albumArtPath) = ''")
    org.springframework.data.domain.Page<Track> findTracksMissingArtwork(org.springframework.data.domain.Pageable pageable);

    long count();
}
