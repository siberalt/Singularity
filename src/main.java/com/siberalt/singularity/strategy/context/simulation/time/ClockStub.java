package com.siberalt.singularity.strategy.context.simulation.time;

import com.siberalt.singularity.simulation.SimulationClock;

import java.time.Instant;

public class ClockStub implements SimulationClock {
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
