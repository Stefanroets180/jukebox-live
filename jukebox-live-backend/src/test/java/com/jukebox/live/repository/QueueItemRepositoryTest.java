package com.jukebox.live.repository;

import com.jukebox.live.entity.QueueItem;
import com.jukebox.live.entity.Track;
import com.jukebox.live.entity.User;
import com.jukebox.live.entity.User.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
public class QueueItemRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private QueueItemRepository queueItemRepository;

    @Test
    public void testFindMaxPositionByPartyId_EmptyQueue() {
        UUID partyId = UUID.randomUUID();

        Integer maxPos = queueItemRepository.findMaxPositionByPartyId(partyId);

        assertThat(maxPos).isEqualTo(0);
    }

    @Test
    public void testFindMaxPositionByPartyId_ExistingItems() {
        UUID partyId = UUID.randomUUID();

        User user = User.builder()
                .username("testuser")
                .passwordHash("hash")
                .credits(10)
                .role(Role.USER)
                .build();
        entityManager.persist(user);

        Track track = Track.builder()
                .title("Test Track")
                .artist("Artist")
                .filePath("/path/to/track.mp3")
                .build();
        entityManager.persist(track);

        QueueItem item1 = QueueItem.builder()
                .partyId(partyId)
                .trackId(track.getId())
                .requestedBy(user.getId())
                .position(1)
                .status(QueueItem.Status.PENDING)
                .build();
        entityManager.persist(item1);

        QueueItem item2 = QueueItem.builder()
                .partyId(partyId)
                .trackId(track.getId())
                .requestedBy(user.getId())
                .position(2)
                .status(QueueItem.Status.PENDING)
                .build();
        entityManager.persist(item2);

        entityManager.flush();

        Integer maxPos = queueItemRepository.findMaxPositionByPartyId(partyId);

        assertThat(maxPos).isEqualTo(2);
    }

    @Test
    public void testFindMaxPositionByPartyId_IndependentParties() {
        UUID party1 = UUID.randomUUID();
        UUID party2 = UUID.randomUUID();

        Integer max1 = queueItemRepository.findMaxPositionByPartyId(party1);
        Integer max2 = queueItemRepository.findMaxPositionByPartyId(party2);

        assertThat(max1).isEqualTo(0);
        assertThat(max2).isEqualTo(0);
    }
}
