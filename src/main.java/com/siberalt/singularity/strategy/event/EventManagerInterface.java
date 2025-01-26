package com.siberalt.singularity.strategy.event;

public interface EventManagerInterface {
    void attach(String eventId, EventHandlerInterface handler);

    void detach(String eventId, EventHandlerInterface handler);

    void trigger(String eventId, Event event);
}
