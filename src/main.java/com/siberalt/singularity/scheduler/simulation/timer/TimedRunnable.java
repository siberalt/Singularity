package com.siberalt.singularity.scheduler.simulation.timer;

/**
 * Interface for Runnables that can provide their execution time.
 */
public interface TimedRunnable extends Runnable {
    /**
     * Gets the execution time of the Runnable.
     *
     * @return the execution time in nanoseconds
     */
    long getExecutionTime();
}
