package com.siberalt.singularity.scheduler.testutil;

import com.siberalt.singularity.scheduler.ExecutionIterator;
import com.siberalt.singularity.scheduler.ExecutionIteratorFactory;

import java.time.Duration;
import java.time.Instant;

public record TestExecution(ExecutionIterator executionIterator, Duration executionTime) {
    public static TestExecution createIntervalFixed(Instant startTime, Duration interval, int maxExecutionCount) {
        return new TestExecution(
            ExecutionIteratorFactory.createIntervalFixed(startTime, interval, maxExecutionCount), null
        );
    }

    public static TestExecution createIntervalFixed(Duration interval, int maxExecutionCount) {
        return new TestExecution(
            ExecutionIteratorFactory.createIntervalFixed(interval, maxExecutionCount), null
        );
    }

    public static TestExecution createIntervalDelayed(Instant startTime, Duration interval, int maxExecutionCount, Duration executionTime) {
        return new TestExecution(
            ExecutionIteratorFactory.createIntervalDelayed(startTime, interval, maxExecutionCount), executionTime
        );
    }

    public static TestExecution createIntervalDelayed(Duration interval, int maxExecutionCount, Duration executionTime) {
        return new TestExecution(
            ExecutionIteratorFactory.createIntervalDelayed(interval, maxExecutionCount), executionTime
        );
    }
}
