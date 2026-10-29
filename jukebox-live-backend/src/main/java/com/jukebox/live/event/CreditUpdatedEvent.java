package com.jukebox.live.event;

import org.springframework.context.ApplicationEvent;

import java.util.UUID;

public class CreditUpdatedEvent extends ApplicationEvent {
    private final UUID userId;
    private final Integer credits;

    public CreditUpdatedEvent(Object source, UUID userId, Integer credits) {
        super(source);
        this.userId = userId;
        this.credits = credits;
    }

    public UUID getUserId() {
        return userId;
    }

    public Integer getCredits() {
        return credits;
    }
}
