package com.siberalt.singularity.scheduler.simulation.timer;

public class TimedRunnableDecorator implements TimedRunnable {
    private final Runnable runnable;
    private final long executionTime;

    public TimedRunnableDecorator(Runnable runnable, long executionTime) {
        this.runnable = runnable;
        this.executionTime = executionTime;
    }

    @Override
    public long getExecutionTime() {
        return this.executionTime;
    }

    @Override
    public void run() {
        // Execute the wrapped runnable
        this.runnable.run();
    }
}
