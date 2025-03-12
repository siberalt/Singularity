package com.siberalt.singularity.scheduler;

import java.time.Duration;
import java.time.Instant;

public class Schedule {
    public enum ExecutionType {
        FIXED_RATE,
        FIXED_DELAYED,
        DEFAULT
    }

    protected Instant startTime;
    protected Duration interval;
    protected ExecutionType executionType = ExecutionType.DEFAULT;
    protected int totalCalls = 0;

    public Instant getStartTime() {
        return startTime;
    }

    public Schedule setStartTime(Instant startTime) {
        this.startTime = startTime;

        return this;
    }

    public Duration getInterval() {
        return interval;
    }

    public Schedule setInterval(Duration interval) {
        this.interval = interval;

        return this;
    }

    public ExecutionType getExecutionType() {
        return executionType;
    }

    public Schedule setExecutionType(ExecutionType executionType) {
        this.executionType = executionType;

        return this;
    }

    public void incrementTotalCalls() {
        totalCalls++;
    }

    public int getTotalCalls() {
        return totalCalls;
    }

    public static Schedule of(Instant startTime, Duration interval, ExecutionType executionType) {
        return new Schedule()
                .setStartTime(startTime)
                .setInterval(interval)
                .setExecutionType(executionType);
    }

    public static Schedule of(Duration interval, ExecutionType executionType) {
        return new Schedule()
            .setInterval(interval)
            .setExecutionType(executionType);
    }
}
