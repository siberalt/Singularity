package com.siberalt.singularity.strategy.simulation.runner;

import com.siberalt.singularity.shared.TimeRange;
import com.siberalt.singularity.simulation.EventSimulator;

public interface EventSimulatorInitializer {
    void initialize(TimeRange timeRange, String accountId, EventSimulator simulator);
}
