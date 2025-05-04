package com.siberalt.singularity.scheduler.simulation.timer;

/**
 * Interface for measuring the execution time of a Runnable task.
 * Implementations of this interface should provide a method to execute
 * a Runnable and return the time taken for its execution.
 */
public interface RunnableTimer {

    /**
     * Executes the given Runnable and measures its execution time.
     *
     * @param runnable the Runnable task to be executed
     * @return the execution time of the Runnable in nanoseconds
     */
    long run(Runnable runnable);
}
