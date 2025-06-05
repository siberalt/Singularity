package com.siberalt.singularity.event;

import java.util.UUID;

public abstract class Event {
    protected UUID id;

    public Event(UUID id) {
        this.id = id;
    }

    public UUID getId() {
        return id;
    }

    abstract public boolean equals(Object o);

    abstract public int hashCode();
}
