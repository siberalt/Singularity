package com.siberalt.singularity.strategy.context.simulation.time;

import com.siberalt.singularity.simulation.SimulationTimeSynchronizerInterface;

import java.time.Instant;

public class SimulationTimeSynchronizer implements SimulationTimeSynchronizerInterface {
    protected Instant currentTime;

    @Override
    public Instant currentTime() {
        return currentTime;
    }

    @Override
    public void syncCurrentTime(Instant currentTime) {
        this.currentTime = currentTime;
    }
}
