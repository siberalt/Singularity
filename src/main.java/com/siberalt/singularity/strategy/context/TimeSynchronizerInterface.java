package com.siberalt.singularity.strategy.context;

import java.time.Instant;

public interface TimeSynchronizerInterface {
    Instant currentTime();
}
