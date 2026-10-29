package com.jukebox.live.event;

import org.springframework.context.ApplicationEvent;

import java.util.UUID;

public class QueueUpdatedEvent extends ApplicationEvent {
    private final UUID partyId;

    public QueueUpdatedEvent(Object source, UUID partyId) {
        super(source);
        this.partyId = partyId;
    }

    public UUID getPartyId() {
        return partyId;
    }
}
