package com.siberalt.singularity.strategy.scheduler;

import com.siberalt.singularity.strategy.StrategyInterface;
import com.siberalt.singularity.strategy.context.ContextAwareInterface;

public interface SchedulerInterface extends ContextAwareInterface {
    void schedule(StrategyInterface strategy, Schedule schedule);

    void stop(StrategyInterface strategy, boolean mayInterruptRunning);

    void stopAll();
}
