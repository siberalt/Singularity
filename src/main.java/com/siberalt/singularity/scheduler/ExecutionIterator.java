package com.siberalt.singularity.scheduler;

import com.siberalt.singularity.strategy.context.Clock;

/**
 * Represents a schedule for task execution.
 * This interface defines a method to calculate the next execution time
 * based on the provided clock and the duration of the previous execution.
 */
public interface ExecutionIterator {
    /**
     * Calculates the details of the next scheduled execution.
     *
     * @param clock The clock instance used to determine the current time.
     * @return A `ScheduledExecution` object containing the details of the next execution.
     */
    Execution getNext(Clock clock);
}
