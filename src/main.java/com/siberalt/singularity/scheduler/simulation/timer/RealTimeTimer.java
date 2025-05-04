package com.siberalt.singularity.scheduler.simulation.timer;

public class RealTimeTimer implements RunnableTimer {
    /**
     * Executes the given Runnable and measures its execution time.
     *
     * @param runnable the Runnable task to be executed
     * @return the execution time of the Runnable in nanoseconds
     */
    @Override
    public long run(Runnable runnable) {
        long startTime = System.nanoTime();
        runnable.run();
        return System.nanoTime() - startTime;
    }
}
