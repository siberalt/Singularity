package com.siberalt.singularity.event.subscription.exception;

public class UnsupportedEventTypeException extends RuntimeException {
    private final Class<?> eventType;

    public UnsupportedEventTypeException(Class<?> eventType) {
        super("Unsupported event type: " + eventType.getSimpleName());
        this.eventType = eventType;
    }

    public Class<?> getEventType() {
        return eventType;
    }
}
