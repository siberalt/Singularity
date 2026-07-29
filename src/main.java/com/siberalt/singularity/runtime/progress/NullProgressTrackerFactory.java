package com.siberalt.singularity.runtime.progress;

public class NullProgressTrackerFactory implements ProgressTrackerFactory {
    @Override
    public ProgressTracker create(int total) {
        return new NullProgressTracker();
    }
}
