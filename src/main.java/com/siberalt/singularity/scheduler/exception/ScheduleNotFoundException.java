package com.siberalt.singularity.scheduler.exception;

import java.util.UUID;

public class ScheduleNotFoundException extends RuntimeException {
    private final UUID scheduleId;

    public ScheduleNotFoundException(String message, UUID scheduleId) {
        super(message);
        this.scheduleId = scheduleId;
    }

    public UUID getScheduleId() {
        return scheduleId;
    }
}
