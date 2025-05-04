package com.siberalt.singularity.scheduler;

import com.siberalt.singularity.strategy.context.Clock;

import java.time.Duration;
import java.time.Instant;

public class SingleCallSchedule implements ExecutionIterator {
    private final Instant executionTime;
    private boolean isExecuted = false;

    public SingleCallSchedule(Instant executionTime) {
        this.executionTime = executionTime;
    }

    @Override
    public Execution getNext(Clock clock) {
        if (isExecuted) {
            return null; // No more executions after the first one
        }

        isExecuted = true; // Mark as executed
        Duration period = Duration.between(clock.currentTime(), executionTime);

        return new Execution(period, ExecutionType.FIXED_RATE);
    }
}
