package com.siberalt.singularity.scheduler.executor;

import com.siberalt.singularity.scheduler.Execution;
import com.siberalt.singularity.scheduler.Schedule;
import com.siberalt.singularity.scheduler.Scheduler;
import com.siberalt.singularity.scheduler.exception.InvalidScheduleException;
import com.siberalt.singularity.scheduler.exception.ScheduleNotFoundException;
import com.siberalt.singularity.strategy.context.Clock;
import com.siberalt.singularity.strategy.context.execution.time.RealTimeClock;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

public class ExecutorServiceScheduler<idType> implements Scheduler<idType> {
    private class TaskWrapper implements Runnable {
        private final Runnable task;
        private final Schedule<idType> schedule;

        public TaskWrapper(Runnable task, Schedule<idType> schedule) {
            this.task = task;
            this.schedule = schedule;
        }

        @Override
        public void run() {
            try {
                idType scheduleId = schedule.id();
                ScheduledFutureDecorator<?> future = runnableFutures.get(scheduleId);
                if (!isFutureActive(future)) {
                    return;
                }

                task.run();
                Execution nextExecution = schedule.iterator().getNext(clock);

                if (nextExecution == null) {
                    stop(scheduleId);
                    return;
                }

                if (!nextExecution.equals(scheduledExecutions.get(scheduleId))) {
                    if (future.isCancelled() || future.isDone()) {
                        return;
                    }

                    scheduleExecution(scheduleId, nextExecution, this);
                }
            } catch (Throwable e) {
                System.err.println("Error during task execution: " + e.getMessage());
                e.printStackTrace();
                throw e;
            }
        }
    }

    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(5);
    private final HashMap<idType, ScheduledFutureDecorator<?>> runnableFutures = new HashMap<>();
    private final Clock clock;
    private final Map<idType, Schedule<idType>> schedules = new HashMap<>();
    private final Map<idType, Execution> scheduledExecutions = new HashMap<>();

    public ExecutorServiceScheduler(Clock clock) {
        this.clock = clock;
    }

    public ExecutorServiceScheduler() {
        this.clock = new RealTimeClock();
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable task, Schedule<idType> schedule) {
        Execution scheduledExecution = schedule.iterator().getNext(clock);

        if (scheduledExecution == null) {
            throw new InvalidScheduleException("Schedule cannot be null", schedule);
        }

        schedules.put(schedule.id(), schedule);
        return scheduleExecution(schedule.id(), scheduledExecution, new TaskWrapper(task, schedule));
    }

    public synchronized void stop(idType scheduleId) {
        if (!runnableFutures.containsKey(scheduleId)) {
            throw new ScheduleNotFoundException("Schedule not found", scheduleId);
        }

        if (!schedules.containsKey(scheduleId)) {
            throw new ScheduleNotFoundException("Schedule not found", scheduleId);
        }

        finishExecution(scheduleId);
        clearSchedule(scheduleId);
    }

    private void finishExecution(idType scheduleId) {
        ScheduledFutureDecorator<?> future = runnableFutures.get(scheduleId);

        if (isFutureActive(future)) {
            future.finish();
        }
    }

    private boolean isFutureActive(ScheduledFutureDecorator<?> future) {
        return future != null && !future.isCancelled() && !future.isDone();
    }

    private void clearSchedule(idType scheduleId) {
        schedules.remove(scheduleId);
        scheduledExecutions.remove(scheduleId);
        runnableFutures.remove(scheduleId);
    }

    private ScheduledFuture<?> scheduleExecution(
        idType scheduleId,
        @Nonnull Execution execution,
        @Nonnull Runnable runnable
    ) {
        long period = execution.period().toMillis();
        scheduledExecutions.put(scheduleId, execution);
        period = Math.max(period, 1);

        ScheduledFuture<?> future = switch (execution.executionType()) {
            case FIXED_DELAYED -> executor.scheduleWithFixedDelay(runnable, period, period, TimeUnit.MILLISECONDS);
            case FIXED_RATE -> executor.scheduleAtFixedRate(runnable, period, period, TimeUnit.MILLISECONDS);
        };

        ScheduledFutureDecorator<?> futureDecorator = runnableFutures.computeIfAbsent(
            scheduleId,
            k -> new ScheduledFutureDecorator<>(() -> clearSchedule(scheduleId), clock)
        );
        futureDecorator.replace(future);

        return futureDecorator;
    }
}
