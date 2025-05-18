package com.siberalt.singularity.simulation;

import com.siberalt.singularity.strategy.context.Clock;

public interface TimeDependentUnit {
    void applyClock(Clock clock);

    void tick();
}
