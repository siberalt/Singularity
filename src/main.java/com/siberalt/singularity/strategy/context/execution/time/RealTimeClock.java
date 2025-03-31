package com.siberalt.singularity.strategy.context.execution.time;

import com.siberalt.singularity.strategy.context.Clock;

import java.time.Instant;

public class RealTimeClock implements Clock {
    @Override
    public Instant currentTime() {
        return Instant.now();
    }
}
