package com.siberalt.singularity.simulation;

import com.siberalt.singularity.strategy.context.TimeSynchronizerInterface;

import java.time.Instant;

public interface SimulationTimeSynchronizerInterface extends TimeSynchronizerInterface {
    void syncCurrentTime(Instant currentTime);
}
