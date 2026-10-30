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

    Optional<Track> findFirstByArtistIgnoreCaseAndAlbumIgnoreCaseAndArtworkCacheKeyIsNotNull(String artist, String album);

    @Query("SELECT t FROM Track t WHERE " +
           "LOWER(t.title) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(t.artist) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(t.album) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(t.genre) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<Track> search(@Param("query") String query);

    @Query("SELECT t FROM Track t WHERE t.albumArtPath IS NULL OR t.albumArtPath = '' ORDER BY LOWER(t.artist), LOWER(t.title)")
    org.springframework.data.domain.Page<Track> findTracksMissingArtwork(org.springframework.data.domain.Pageable pageable);

    @Query("SELECT DISTINCT t.artist FROM Track t WHERE t.artist IS NOT NULL ORDER BY LOWER(t.artist)")
    List<String> findArtists();

    @Query("SELECT DISTINCT t.genre FROM Track t WHERE t.genre IS NOT NULL AND t.genre != '' ORDER BY LOWER(t.genre)")
    List<String> findGenres();

    @Query("SELECT t FROM Track t WHERE LOWER(t.artist) = LOWER(:artist) ORDER BY LOWER(t.album), LOWER(t.title), t.id")
    List<Track> findByArtist(@Param("artist") String artist);

    @Query("SELECT t FROM Track t WHERE LOWER(t.artist) = LOWER(:artist) AND LOWER(t.album) = LOWER(:album) ORDER BY LOWER(t.title), t.id")
    List<Track> findByArtistAndAlbum(@Param("artist") String artist, @Param("album") String album);

    @Query("SELECT t FROM Track t WHERE LOWER(t.genre) = LOWER(:genre) ORDER BY LOWER(t.artist), LOWER(t.album), LOWER(t.title), t.id")
    List<Track> findByGenre(@Param("genre") String genre);

    long count();
}
