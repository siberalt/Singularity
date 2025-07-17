package com.siberalt.singularity.simulation;

import java.time.Instant;

public interface Initializable extends SimulationUnit {
    void init(Instant startTime, Instant endTime);
}
