package com.siberalt.singularity.simulation;

import java.time.Instant;
import java.util.UUID;

public class Event {
    protected String id;
    protected Instant timePoint;
    protected EventInvoker eventInvoker;

    private Event(String id, Instant timePoint, EventInvoker eventInvoker) {
        this.id = id;
        this.timePoint = timePoint;
        this.eventInvoker = eventInvoker;
    }

    public String getId() {
        return id;
    }

    public Instant getTimePoint() {
        return timePoint;
    }

    public EventInvoker getEventInvoker() {
        return eventInvoker;
    }

    public static Event create(Instant timePoint, EventInvoker eventInvoker) {
        return new Event(UUID.randomUUID().toString(), timePoint, eventInvoker);
    }
}
