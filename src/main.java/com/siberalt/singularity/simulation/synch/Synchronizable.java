package com.siberalt.singularity.simulation.synch;

import com.siberalt.singularity.simulation.SimulationUnit;

public interface Synchronizable extends SimulationUnit {
    void synchWith(TaskSynchronizer synchronizer);
}
