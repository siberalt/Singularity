package com.siberalt.singularity.simulation;

import com.siberalt.singularity.strategy.context.Clock;

public interface TimeDependentUnitInterface {
    void applyClock(Clock clock);

    void tick();
}
