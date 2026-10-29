package com.jukebox.live.repository;

import com.jukebox.live.entity.QueueItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface QueueItemRepository extends JpaRepository<QueueItem, UUID>, QueueItemRepositoryCustom {

    List<QueueItem> findByPartyIdAndStatusOrderByPositionAsc(UUID partyId, QueueItem.Status status);

    List<QueueItem> findByPartyIdOrderByPositionAsc(UUID partyId);

    List<QueueItem> findByTrackIdAndStatusIn(UUID trackId, List<QueueItem.Status> statuses);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE QueueItem q SET q.progressSeconds = :progressSeconds " +
            "WHERE q.id = :queueItemId AND q.partyId = :partyId AND q.status = :status")
    int updateProgressForQueueItem(@Param("queueItemId") UUID queueItemId,
                                   @Param("partyId") UUID partyId,
                                   @Param("status") QueueItem.Status status,
                                   @Param("progressSeconds") Integer progressSeconds);

    @Query("SELECT COALESCE(MAX(q.position), 0) FROM QueueItem q WHERE q.partyId = :partyId")
    Integer findMaxPositionByPartyId(@Param("partyId") UUID partyId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE QueueItem q SET q.status = 'COMPLETED' " +
            "WHERE q.id = :queueItemId AND q.partyId = :partyId AND q.status = 'PLAYING'")
    int completeQueueItem(@Param("queueItemId") UUID queueItemId,
                          @Param("partyId") UUID partyId);

    Optional<QueueItem> findFirstByPartyIdAndStatusOrderByPositionAsc(UUID partyId, QueueItem.Status status);

    long countByPartyIdAndStatus(UUID partyId, QueueItem.Status status);
}
