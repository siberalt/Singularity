package com.siberalt.singularity.runtime.progress;

public class ConsoleProgressTrackerFactory implements ProgressTrackerFactory {
    @Override
    public ProgressTracker create(int total) {
        System.out.println("Total tasks: " + total);
        return new ConsoleProgressTracker(total);
    }
}
