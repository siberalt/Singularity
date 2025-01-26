package com.siberalt.singularity.strategy.scheduler;

import com.siberalt.singularity.strategy.StrategyInterface;
import com.siberalt.singularity.strategy.context.AbstractContext;
import com.siberalt.singularity.strategy.scheduler.exception.InvalidScheduleException;

import javax.annotation.Nonnull;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.concurrent.*;

public class Scheduler implements SchedulerInterface {
    protected ScheduledExecutorService executor = Executors.newScheduledThreadPool(5);

    protected HashMap<String, ScheduledFuture<?>> strategyFutures = new HashMap<>();

    protected AbstractContext<?> context;

    @Override
    public void applyContext(AbstractContext<?> context) {
        this.context = context;
    }

    @Override
    public void schedule(StrategyInterface strategy, Schedule schedule) {
        StrategyTask task = new StrategyTask(context, strategy);

        switch (schedule.executionType) {
            case DEFAULT -> scheduleDefault(task, schedule);
            case FIXED_DELAYED -> scheduleAtFixedDelayed(task, schedule);
            case FIXED_RATE -> scheduleAtFixedRate(task, schedule);
        }
    }

    @Override
    public void stop(StrategyInterface strategy, boolean mayInterruptRunning) {
        if (!strategyFutures.containsKey(strategy.getId())) {
            // TODO: throw exception
        }

        strategyFutures.get(strategy.getId()).cancel(mayInterruptRunning);
        strategyFutures.remove(strategy.getId());
    }

    @Override
    public void stopAll() {
        executor.shutdownNow();
    }

    protected void scheduleDefault(@Nonnull StrategyTask strategyTask, @Nonnull Schedule schedule) {
        ScheduledFuture<?> future = executor.schedule(strategyTask, getDelay(schedule), TimeUnit.MILLISECONDS);
        strategyFutures.put(strategyTask.getStrategy().getId(), future);
    }

    protected void scheduleAtFixedRate(@Nonnull StrategyTask strategyTask, @Nonnull Schedule schedule) {
        if (null == schedule.getInterval()) {
            throw new InvalidScheduleException("Period must be set", schedule);
        }

        ScheduledFuture<?> future = executor.scheduleAtFixedRate(
                strategyTask, getDelay(schedule), schedule.getInterval().toMillis(), TimeUnit.MILLISECONDS
        );
        strategyFutures.put(strategyTask.getStrategy().getId(), future);
    }

    protected void scheduleAtFixedDelayed(@Nonnull StrategyTask strategyTask, @Nonnull Schedule schedule) {
        if (null == schedule.getInterval()) {
            throw new InvalidScheduleException("Period must be set", schedule);
        }

        ScheduledFuture<?> future = executor.scheduleWithFixedDelay(
                strategyTask, getDelay(schedule), schedule.getInterval().toMillis(), TimeUnit.MILLISECONDS
        );
        strategyFutures.put(strategyTask.getStrategy().getId(), future);
    }

    protected long getDelay(Schedule schedule) {
        return ChronoUnit.MILLIS.between(schedule.getStartTime(), context.getCurrentTime());
    }
}
