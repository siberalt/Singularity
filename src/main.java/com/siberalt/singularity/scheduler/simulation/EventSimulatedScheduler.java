package com.siberalt.singularity.scheduler.simulation;

import com.siberalt.singularity.scheduler.Execution;
import com.siberalt.singularity.scheduler.ExecutionType;
import com.siberalt.singularity.scheduler.Schedule;
import com.siberalt.singularity.scheduler.exception.ScheduleNotFoundException;
import com.siberalt.singularity.scheduler.simulation.timer.RunnableTimer;
import com.siberalt.singularity.scheduler.simulation.timer.SimulatedTimer;
import com.siberalt.singularity.simulation.Event;
import com.siberalt.singularity.simulation.EventInvoker;
import com.siberalt.singularity.simulation.EventObserver;
import com.siberalt.singularity.simulation.TimeDependentUnit;
import com.siberalt.singularity.simulation.synch.Synchronizable;
import com.siberalt.singularity.simulation.synch.TaskSynchronizer;
import com.siberalt.singularity.strategy.context.Clock;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.logging.Logger;

public class EventSimulatedScheduler<idType> implements
    SimulationScheduler<idType>,
    EventInvoker,
    Synchronizable,
    TimeDependentUnit {

    public record ExecutionInfo<idType>(idType scheduleId, long executionTime) {
    }

    private static final Logger logger = Logger.getLogger(EventSimulatedScheduler.class.getName());

    private EventObserver eventObserver;
    private final Map<idType, Schedule<idType>> schedules = new ConcurrentHashMap<>();
    private final TaskExecutionTable<idType> taskExecutionTable = new TaskExecutionTable<>();
    private final Map<idType, Event> events = new HashMap<>();
    private Clock clock;
    private RunnableTimer runnableTimer = new SimulatedTimer();
    private final Set<idType> stoppedSchedules = new HashSet<>();
    private final HashMap<idType, SimulatedScheduledFuture<idType>> futures = new HashMap<>();
    private TaskSynchronizer synchronizer;

    public EventSimulatedScheduler<idType> setRunnableTimer(RunnableTimer runnableTimer) {
        this.runnableTimer = runnableTimer;
        return this;
    }

    @Override
    public void synchWith(TaskSynchronizer synchronizer) {
        this.synchronizer = synchronizer;
    }

    @Override
    public synchronized ScheduledFuture<?> schedule(Runnable task, Schedule<idType> schedule) {
        idType scheduleId = schedule.id();

        if (scheduleId == null) {
            throw new IllegalArgumentException("Schedule ID cannot be null");
        }

        if (schedules.containsKey(scheduleId)) {
            throw new IllegalArgumentException("Schedule ID already exists: " + scheduleId);
        }

        logger.info("Scheduling " + scheduleId + " with " + schedule);
        schedules.put(scheduleId, schedule);
        taskExecutionTable.registerTask(scheduleId, task);
        Event event = createEvent(schedule, 0);

        if (event == null) {
            logger.warning("No event created for schedule " + scheduleId);
            return null;
        }

        SimulatedScheduledFuture<idType> future = new SimulatedScheduledFuture<>(synchronizer, () -> stop(scheduleId));
        futures.put(scheduleId, future);
        registerEvent(scheduleId, event);

        return future;
    }

    public void stop(idType scheduleId) {
        if (!schedules.containsKey(scheduleId)) {
            throw new ScheduleNotFoundException("Schedule not found", scheduleId);
        }

        logger.info("Stopping " + scheduleId);
        Event event = events.get(scheduleId);

        if (event != null) {
            eventObserver.cancelEvent(event);
        }

        taskExecutionTable.cancelExecution(scheduleId);
        finish(scheduleId);
    }

    @Override
    public void applyClock(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void tick() {
        Instant time = clock.currentTime();
        if (!taskExecutionTable.hasPlannedExecutions(time)) {
            return;
        }

        List<ExecutionInfo<idType>> invokedSchedules = new ArrayList<>();
        for (idType scheduleId : taskExecutionTable.getPlannedExecutions(time)) {
            Runnable task = taskExecutionTable.getTask(scheduleId);
            Event event = events.get(scheduleId);

            if (event.getTimePoint().equals(time)) {
                try {
                    invokedSchedules.add(new ExecutionInfo<>(scheduleId, runnableTimer.run(task)));
                } catch (Exception e) {
                    logger.warning("Error executing task for schedule " + scheduleId + ": " + e.getMessage());
                    SimulatedScheduledFuture<idType> future = futures.get(scheduleId);
                    future.setException(e);
                    future.finish();
                }
            }
        }

        registerNextExecutionEvents(invokedSchedules);
        taskExecutionTable.finishPlannedExecutions(time);
    }

    private void finish(Schedule<idType> schedule) {
        idType scheduleId = schedule.id();
        SimulatedScheduledFuture<idType> future = futures.get(scheduleId);
        future.finish();
        futures.remove(scheduleId);
    }

    private void finish(idType scheduleId) {
        Schedule<idType> schedule = schedules.get(scheduleId);
        if (schedule != null) {
            finish(schedule);
            stoppedSchedules.add(schedule.id());
        }
    }

    private void registerNextExecutionEvents(List<ExecutionInfo<idType>> executionInfoList) {
        for (ExecutionInfo<idType> info : executionInfoList) {
            resetEvent(info.scheduleId());

            if (stoppedSchedules.contains(info.scheduleId())) {
                continue;
            }

            Schedule<idType> schedule = schedules.get(info.scheduleId());
            Event event = createEvent(schedule, info.executionTime());

            if (event == null) {
                finish(schedule);
                continue;
            }

            registerEvent(info.scheduleId(), event);
        }

        for (idType scheduleId : stoppedSchedules) {
            schedules.remove(scheduleId);
        }
    }

    @Override
    public void observeEventsBy(EventObserver observer) {
        this.eventObserver = observer;
    }

    private Event createEvent(Schedule<idType> schedule, long executionTime) {
        Execution nextExecution = schedule.iterator().getNext(clock);

        if (nextExecution == null) {
            return null;
        }

        long periodMillis = nextExecution.period().toMillis();
        long beforeNextCall = nextExecution.executionType() == ExecutionType.FIXED_RATE
            ? periodMillis
            : Math.max(executionTime, periodMillis);

        Instant nextTime = clock.currentTime().plus(beforeNextCall, ChronoUnit.MILLIS);
        return Event.create(nextTime, this);
    }

    private void registerEvent(idType scheduleId, Event event) {
        events.put(scheduleId, event);
        taskExecutionTable.planExecution(event.getTimePoint(), scheduleId);
        eventObserver.scheduleEvent(event);
        long delayNanos = Duration.between(clock.currentTime(), event.getTimePoint()).toNanos();
        futures.get(scheduleId).setDelay(delayNanos);
    }

    private void resetEvent(idType scheduleId) {
        events.remove(scheduleId);
    }
}
