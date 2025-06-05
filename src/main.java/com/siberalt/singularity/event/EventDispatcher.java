package com.siberalt.singularity.event;

import java.util.concurrent.CompletableFuture;

/**
 * Interface for dispatching events to registered handlers.
 */
public interface EventDispatcher {
    /**
     * Triggers an event, invoking all associated handlers.
     *
     * @param event the event to trigger
     */
    CompletableFuture<Void> dispatch(Event event);
}
