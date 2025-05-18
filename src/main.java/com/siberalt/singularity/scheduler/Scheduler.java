package com.siberalt.singularity.scheduler;

import java.util.concurrent.ScheduledFuture;

public interface Scheduler<idType> {
    ScheduledFuture<?> schedule(Runnable task, Schedule<idType> schedule);
}
