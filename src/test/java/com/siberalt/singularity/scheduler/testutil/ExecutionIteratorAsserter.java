package com.siberalt.singularity.scheduler.testutil;

import com.siberalt.singularity.scheduler.Execution;
import com.siberalt.singularity.scheduler.ExecutionIterator;
import com.siberalt.singularity.scheduler.ExecutionType;
import com.siberalt.singularity.strategy.context.Clock;
import org.junit.jupiter.api.Assertions;

import java.time.Duration;
import java.time.Instant;

public class ExecutionIteratorAsserter implements ExecutionIterator {
    private static final long TOLERANCE_NANOS = 100000000; // 10 ms in nanoseconds
    private final ExecutionIterator executionIterator;
    private Execution plannedExecution;
    private Instant previousExecutionTime;

    public ExecutionIteratorAsserter(ExecutionIterator executionIterator) {
        this.executionIterator = executionIterator;
    }

    @Override
    public Execution getNext(Clock clock) {
        if (null != plannedExecution && plannedExecution.executionType().equals(ExecutionType.FIXED_RATE)) {
            long difference = Math.abs(
                Duration.between(previousExecutionTime.plus(plannedExecution.period()), clock.currentTime()).toNanos()
            );
            Assertions.assertTrue(
                 difference <= TOLERANCE_NANOS,
                String.format(
                    "Expected: <%s> but was: <%s>. Difference: %d nanoseconds",
                    previousExecutionTime.plus(plannedExecution.period()),
                    clock.currentTime(),
                    difference
                )
            );
        }

        plannedExecution = executionIterator.getNext(clock);
        previousExecutionTime = clock.currentTime();

        return plannedExecution;
    }

    @Override
    public String toString() {
        return executionIterator.toString();
    }
}
