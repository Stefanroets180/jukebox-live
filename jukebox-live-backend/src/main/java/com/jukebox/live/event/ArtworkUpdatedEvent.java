package com.jukebox.live.event;

import org.springframework.context.ApplicationEvent;

import java.util.UUID;

public class ArtworkUpdatedEvent extends ApplicationEvent {
    private final UUID trackId;

    public ArtworkUpdatedEvent(Object source, UUID trackId) {
        super(source);
        this.trackId = trackId;
    }

    public UUID getTrackId() {
        return trackId;
    }
}
