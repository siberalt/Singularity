package com.siberalt.singularity.scheduler;

import java.time.Duration;
import java.time.Instant;

public class ExecutionIteratorFactory {
    public static ExecutionIterator createInterval(
        Instant startTime,
        Duration interval,
        ExecutionType executionType,
        int maxExecutionCount
    ) {
        return new IntervalIterator(startTime, interval, executionType, maxExecutionCount);
    }

    public static ExecutionIterator createIntervalDelayed(Instant startTime, Duration interval, int maxExecutionCount) {
        return new IntervalIterator(startTime, interval, ExecutionType.FIXED_DELAYED, maxExecutionCount);
    }

    public static ExecutionIterator createIntervalDelayed(Duration interval, int maxExecutionCount) {
        return new IntervalIterator(interval, ExecutionType.FIXED_DELAYED, maxExecutionCount);
    }

    public static ExecutionIterator createIntervalFixed(Instant startTime, Duration interval, int maxExecutionCount) {
        return new IntervalIterator(startTime, interval, ExecutionType.FIXED_RATE, maxExecutionCount);
    }

    public static ExecutionIterator createIntervalFixed(Duration interval, int maxExecutionCount) {
        return new IntervalIterator(interval, ExecutionType.FIXED_RATE, maxExecutionCount);
    }

    public static ExecutionIterator createIntervalFixed(Instant startTime, Duration interval) {
        return new IntervalIterator(startTime, interval, ExecutionType.FIXED_RATE);
    }

    public static ExecutionIterator createInterval(Instant startTime, Duration interval, ExecutionType executionType) {
        return new IntervalIterator(startTime, interval, executionType);
    }

    public static ExecutionIterator createInterval(Duration interval, ExecutionType executionType, int maxExecutionCount) {
        return new IntervalIterator(interval, executionType, maxExecutionCount);
    }

    public static ExecutionIterator createInterval(Duration interval, ExecutionType executionType) {
        return new IntervalIterator(interval, executionType);
    }

    public static ExecutionIterator createInterval(long interval, long delay, ExecutionType executionType) {
        return new IntervalIterator(Duration.ofMillis(interval), Duration.ofMillis(delay), executionType);
    }

    public static ExecutionIterator createInterval(long interval, ExecutionType executionType) {
        return new IntervalIterator(Duration.ofMillis(interval), executionType);
    }

    public static ExecutionIterator createIntervalFixed(long interval) {
        return createInterval(interval, ExecutionType.FIXED_RATE);
    }
    public static ExecutionIterator createIntervalFixed(Duration interval) {
        return createInterval(interval, ExecutionType.FIXED_RATE);
    }

    public static ExecutionIterator createSingleCall(long executionTime) {
        return new SingleCallSchedule(Instant.ofEpochMilli(executionTime));
    }

    public static ExecutionIterator createSingleCall(Instant executionTime) {
        return new SingleCallSchedule(executionTime);
    }

    public static ExecutionIterator createSingleCall(Instant executionTime, long delay) {
        return new SingleCallSchedule(executionTime.plusMillis(delay));
    }

    public static ExecutionIterator createSingleCall(long executionTime, long delay) {
        return new SingleCallSchedule(Instant.ofEpochMilli(executionTime).plusMillis(delay));
    }
}
