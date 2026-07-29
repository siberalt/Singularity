package com.siberalt.singularity.runtime.progress;

public interface ProgressTrackerFactory {
    ProgressTracker create(int total);
}
