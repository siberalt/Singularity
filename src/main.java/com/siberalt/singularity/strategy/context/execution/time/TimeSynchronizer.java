package com.siberalt.singularity.strategy.context.execution.time;

import com.siberalt.singularity.strategy.context.TimeSynchronizerInterface;

import java.time.Instant;

public class TimeSynchronizer implements TimeSynchronizerInterface {
    @Override
    public Instant currentTime() {
        return Instant.now();
    }
}
