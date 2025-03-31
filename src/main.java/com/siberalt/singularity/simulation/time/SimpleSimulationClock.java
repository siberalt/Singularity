package com.siberalt.singularity.simulation.time;

import com.siberalt.singularity.simulation.SimulationClock;

import java.time.Instant;

public class SimpleSimulationClock implements SimulationClock {
    private Instant currentTime;

    @Override
    public Instant currentTime() {
        return currentTime;
    }

    @Override
    public void syncCurrentTime(Instant currentTime) {
        this.currentTime = currentTime;
    }
}
