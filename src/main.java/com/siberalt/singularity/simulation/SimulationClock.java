package com.siberalt.singularity.simulation;

import com.siberalt.singularity.strategy.context.Clock;

import java.time.Instant;

public interface SimulationClock extends Clock {
    void syncCurrentTime(Instant currentTime);
}
