package investtech.strategy.scheduler;

import investtech.strategy.StrategyInterface;
import investtech.strategy.context.AbstractContext;
import investtech.strategy.context.ContextAwareInterface;

public interface SchedulerInterface extends ContextAwareInterface {
    void schedule(StrategyInterface strategy, Schedule schedule);

    void stop(StrategyInterface strategy, boolean mayInterruptRunning);

    void stopAll();
}
