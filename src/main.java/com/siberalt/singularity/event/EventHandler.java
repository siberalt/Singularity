package com.siberalt.singularity.event;

import com.siberalt.singularity.event.subscription.Subscription;

public interface EventHandler<T extends Event> {
    void handle(T event, Subscription subscription);
}
