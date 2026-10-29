package com.jukebox.live.repository;

import java.util.UUID;

public interface QueueItemRepositoryCustom {
    Integer allocateNextPosition(UUID partyId);
}
