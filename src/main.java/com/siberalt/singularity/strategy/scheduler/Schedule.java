package com.siberalt.singularity.strategy.scheduler;

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
    protected int iterations;
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

    public int getIterations() {
        return iterations;
    }

    public Schedule setIterations(int iterations) {
        this.iterations = iterations;
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

    public static Schedule createDefault(Instant currentTime, Duration delay, Duration interval) {
        return new Schedule()
                .setStartTime(currentTime.plus(delay))
                .setInterval(interval)
                .setExecutionType(ExecutionType.DEFAULT);
    }

    public static Schedule createFixedDelayed(Instant currentTime, Duration delay, Duration interval) {
        return new Schedule()
                .setStartTime(currentTime.plus(delay))
                .setInterval(interval)
                .setExecutionType(ExecutionType.FIXED_DELAYED);
    }

    public static Schedule createFixedRate(Instant currentTime, Duration delay, Duration interval) {
        return new Schedule()
                .setStartTime(currentTime.plus(delay))
                .setInterval(interval)
                .setExecutionType(ExecutionType.FIXED_RATE);
    }

    public static Schedule createDefault(Instant startTime, Duration interval) {
        return new Schedule()
                .setStartTime(startTime)
                .setInterval(interval)
                .setExecutionType(ExecutionType.DEFAULT);
    }

    public static Schedule createFixedDelayed(Instant startTime, Duration interval) {
        return new Schedule()
                .setStartTime(startTime)
                .setInterval(interval)
                .setExecutionType(ExecutionType.FIXED_DELAYED);
    }

    public static Schedule createFixedRate(Instant startTime, Duration interval) {
        return new Schedule()
                .setStartTime(startTime)
                .setInterval(interval)
                .setExecutionType(ExecutionType.FIXED_RATE);
    }
}
