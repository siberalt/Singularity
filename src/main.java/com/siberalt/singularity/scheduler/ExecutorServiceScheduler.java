package com.siberalt.singularity.scheduler;

import com.siberalt.singularity.scheduler.exception.InvalidScheduleException;
import com.siberalt.singularity.scheduler.exception.ScheduleNotFoundException;
import com.siberalt.singularity.strategy.context.Clock;
import com.siberalt.singularity.strategy.context.execution.time.RealTimeClock;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

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
                task.run();
                Execution nextExecution = schedule.iterator().getNext(clock);

                if (nextExecution == null) {
                    stop(scheduleId);
                    return;
                }

                if (!nextExecution.equals(scheduledExecutions.get(scheduleId))) {
                    stopExecution(scheduleId);
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
    private final HashMap<idType, ScheduledFuture<?>> runnableFutures = new HashMap<>();
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
    public void schedule(Runnable task, Schedule<idType> schedule) {
        Execution scheduledExecution = schedule.iterator().getNext(clock);

        if (scheduledExecution == null) {
            throw new InvalidScheduleException("Schedule cannot be null", schedule);
        }

        schedules.put(schedule.id(), schedule);
        scheduleExecution(schedule.id(), scheduledExecution, new TaskWrapper(task, schedule));
    }

    @Override
    public void stop(idType scheduleId) {
        if (!runnableFutures.containsKey(scheduleId)) {
            throw new ScheduleNotFoundException("Schedule not found", scheduleId);
        }

        if (!schedules.containsKey(scheduleId)) {
            throw new ScheduleNotFoundException("Schedule not found", scheduleId);
        }

        stopExecution(scheduleId);

        Schedule<idType> schedule = schedules.remove(scheduleId);

        if (schedule.onFinish() != null) {
            schedule.onFinish().accept(schedule);
        }
    }

    private void stopExecution(idType scheduleId) {
        if (!runnableFutures.containsKey(scheduleId)) {
            throw new ScheduleNotFoundException("Schedule not found", scheduleId);
        }

        runnableFutures.get(scheduleId).cancel(true);
        runnableFutures.remove(scheduleId);
    }

    private void scheduleExecution(
        idType scheduleId,
        @Nonnull Execution execution,
        @Nonnull Runnable runnable
    ) {
        long period = execution.period().toMillis();
        scheduledExecutions.put(scheduleId, execution);

        ScheduledFuture<?> future = switch (execution.executionType()) {
            case FIXED_DELAYED -> executor.scheduleWithFixedDelay(runnable, period, period, TimeUnit.MILLISECONDS);
            case FIXED_RATE -> executor.scheduleAtFixedRate(runnable, period, period, TimeUnit.MILLISECONDS);
        };
        runnableFutures.put(scheduleId, future);
    }
}
