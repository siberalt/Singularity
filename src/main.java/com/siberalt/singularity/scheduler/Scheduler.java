package com.siberalt.singularity.scheduler;

public interface Scheduler<idType> {
    void schedule(Runnable task, Schedule<idType> schedule);

    void stop(idType scheduleId);
}
