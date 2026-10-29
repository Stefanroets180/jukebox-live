package com.jukebox.live.event;

import org.springframework.context.ApplicationEvent;

import java.util.UUID;

public class PlaybackUpdatedEvent extends ApplicationEvent {
    private final UUID partyId;
    private final UUID queueItemId;
    private final boolean isPlaying;

    public PlaybackUpdatedEvent(Object source, UUID partyId, UUID queueItemId, boolean isPlaying) {
        super(source);
        this.partyId = partyId;
        this.queueItemId = queueItemId;
        this.isPlaying = isPlaying;
    }

    public UUID getPartyId() {
        return partyId;
    }

    public UUID getQueueItemId() {
        return queueItemId;
    }

    public boolean isPlaying() {
        return isPlaying;
    }
}
