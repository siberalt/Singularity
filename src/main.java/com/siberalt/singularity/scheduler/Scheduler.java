package com.siberalt.singularity.scheduler;

import com.siberalt.singularity.scheduler.exception.InvalidScheduleException;
import com.siberalt.singularity.scheduler.exception.ScheduleNotFoundException;
import com.siberalt.singularity.strategy.context.Clock;

import javax.annotation.Nonnull;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class Scheduler implements SchedulerInterface {
    protected ScheduledExecutorService executor = Executors.newScheduledThreadPool(5);
    protected HashMap<UUID, ScheduledFuture<?>> runnableFutures = new HashMap<>();
    protected Clock timeSynchronizer;

    public Scheduler(Clock timeSynchronizer) {
        this.timeSynchronizer = timeSynchronizer;
    }

    @Override
    public UUID schedule(Runnable task, Schedule schedule) {
        switch (schedule.executionType) {
            case DEFAULT -> scheduleDefault(task, schedule);
            case FIXED_DELAYED -> scheduleAtFixedDelayed(task, schedule);
            case FIXED_RATE -> scheduleAtFixedRate(task, schedule);
        }
        return null;
    }

    @Override
    public void stop(UUID scheduleId, boolean mayInterruptRunning) {
        if (!runnableFutures.containsKey(scheduleId)) {
            throw new ScheduleNotFoundException("Schedule not found", scheduleId);
        }

        runnableFutures.get(scheduleId).cancel(mayInterruptRunning);
        runnableFutures.remove(scheduleId);
    }

    @Override
    public void stopAll() {
        executor.shutdownNow();
    }

    protected void scheduleDefault(@Nonnull Runnable runnable, @Nonnull Schedule schedule) {
        ScheduledFuture<?> future = executor.schedule(runnable, getDelay(schedule), TimeUnit.MILLISECONDS);
        runnableFutures.put(UUID.randomUUID(), future);
    }

    protected void scheduleAtFixedRate(@Nonnull Runnable runnable, @Nonnull Schedule schedule) {
        if (null == schedule.getInterval()) {
            throw new InvalidScheduleException("Period must be set", schedule);
        }

        ScheduledFuture<?> future = executor.scheduleAtFixedRate(
            runnable, getDelay(schedule), schedule.getInterval().toMillis(), TimeUnit.MILLISECONDS
        );
        runnableFutures.put(UUID.randomUUID(), future);
    }

    protected void scheduleAtFixedDelayed(@Nonnull Runnable runnable, @Nonnull Schedule schedule) {
        if (null == schedule.getInterval()) {
            throw new InvalidScheduleException("Period must be set", schedule);
        }

        ScheduledFuture<?> future = executor.scheduleWithFixedDelay(
            runnable, getDelay(schedule), schedule.getInterval().toMillis(), TimeUnit.MILLISECONDS
        );
        runnableFutures.put(UUID.randomUUID(), future);
    }

    protected long getDelay(Schedule schedule) {
        return ChronoUnit.MILLIS.between(schedule.getStartTime(), timeSynchronizer.currentTime());
    }
}
