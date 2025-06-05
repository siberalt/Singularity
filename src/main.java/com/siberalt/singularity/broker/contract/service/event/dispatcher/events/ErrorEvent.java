package com.siberalt.singularity.broker.contract.service.event.dispatcher.events;

import com.siberalt.singularity.event.Event;

import java.util.UUID;

public class ErrorEvent extends Event {
    private final String errorMessage;

    public ErrorEvent(UUID id, String errorMessage) {
        super(id);
        this.errorMessage = errorMessage;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ErrorEvent that)) return false;
        return errorMessage.equals(that.errorMessage);
    }

    @Override
    public int hashCode() {
        return errorMessage.hashCode();
    }
}
