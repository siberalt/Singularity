package com.siberalt.singularity.simulation;

public interface EventInvoker extends SimulationUnit {
    void observeEventsBy(EventObserver observer);
}
