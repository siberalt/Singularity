package com.siberalt.singularity.scheduler;

import java.util.UUID;

public interface SchedulerInterface  {
    UUID schedule(Runnable task, Schedule schedule);

    void stop(UUID scheduleId, boolean mayInterruptRunning);

    void stopAll();
}
