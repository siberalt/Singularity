package com.siberalt.singularity.strategy.scheduler.exception;

import com.siberalt.singularity.strategy.scheduler.Schedule;

public class InvalidScheduleException extends RuntimeException {
    protected Schedule schedule;

    public InvalidScheduleException(String message, Schedule schedule) {
        super(message);
        this.schedule = schedule;
    }

    public Schedule getSchedule() {
        return schedule;
    }
}
