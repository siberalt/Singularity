package com.siberalt.singularity.simulation;

import java.time.Instant;

public interface Initializable {
    void init(Instant startTime, Instant endTime);
}
