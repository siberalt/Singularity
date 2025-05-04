package com.siberalt.singularity.scheduler.exception;

public class ScheduleNotFoundException extends RuntimeException {
    private final Object scheduleId;

    public ScheduleNotFoundException(String message, Object scheduleId) {
        super(message);
        this.scheduleId = scheduleId;
    }

    public Object getScheduleId() {
        return scheduleId;
    }
}
