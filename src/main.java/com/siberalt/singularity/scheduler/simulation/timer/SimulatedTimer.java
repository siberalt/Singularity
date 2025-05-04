package com.siberalt.singularity.scheduler.simulation.timer;

public class SimulatedTimer implements RunnableTimer {
    private long defaultExecutionTime = 1000L; // Default execution time in nanoseconds

    public SimulatedTimer(long defaultExecutionTime) {
        this.defaultExecutionTime = defaultExecutionTime;
    }

    public SimulatedTimer() {
    }

    /**
     * Executes the given Runnable and measures its execution time.
     *
     * @param runnable the Runnable task to be executed
     * @return the execution time of the Runnable in nanoseconds
     */
    @Override
    public long run(Runnable runnable) {
        runnable.run();

        if (runnable instanceof TimedRunnable) {
            // If the runnable is a TimedRunnable, use its execution time
            return ((TimedRunnable) runnable).getExecutionTime();
        }

        return defaultExecutionTime;
    }
}
