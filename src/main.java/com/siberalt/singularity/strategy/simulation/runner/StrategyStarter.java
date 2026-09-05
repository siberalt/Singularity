package com.siberalt.singularity.strategy.simulation.runner;

import com.siberalt.singularity.shared.TimeRange;
import com.siberalt.singularity.strategy.observer.Observer;

public interface StrategyStarter {
    void start(TimeRange timeRange, String accountId, Observer observer);
}
