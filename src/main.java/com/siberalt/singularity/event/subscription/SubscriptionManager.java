package com.siberalt.singularity.event.subscription;

import com.siberalt.singularity.event.Event;
import com.siberalt.singularity.event.EventHandler;

/**
 * Interface for managing subscriptions and their associated event handlers.
 */
public interface SubscriptionManager {

    /**
     * Attaches a spec and its associated event handler.
     *
     * @param spec the spec specification to attach
     * @param handler      the event handler to associate with the spec
     * @return the created Subscription instance
     */
    <T extends Event> Subscription subscribe(SubscriptionSpec<T> spec, EventHandler<T> handler);
}
