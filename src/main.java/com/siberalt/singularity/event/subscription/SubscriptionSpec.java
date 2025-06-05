package com.siberalt.singularity.event.subscription;

import com.siberalt.singularity.event.Event;

public abstract class SubscriptionSpec<T extends Event> {
    private final Class<T> eventType;

    public SubscriptionSpec(Class<T> eventType) {
        this.eventType = eventType;
    }

    public Class<T> getEventType() {
        return eventType;
    }

    public abstract boolean equals(Object obj);

    public abstract int hashCode();
}
