package com.siberalt.singularity.scheduler.simulation;

import com.siberalt.singularity.scheduler.Schedule;
import com.siberalt.singularity.simulation.Event;
import com.siberalt.singularity.simulation.EventInvokerInterface;
import com.siberalt.singularity.simulation.EventObserver;
import com.siberalt.singularity.simulation.TimeDependentUnitInterface;
import com.siberalt.singularity.strategy.context.TimeSynchronizerInterface;

import java.time.Instant;
import java.util.*;
import java.util.logging.Logger;

public class SimulationScheduler implements SimulationSchedulerInterface, EventInvokerInterface, TimeDependentUnitInterface {
    private static final Logger logger = Logger.getLogger(SimulationScheduler.class.getName());

    protected EventObserver eventObserver;
    protected Map<UUID, Schedule> schedules = new HashMap<>();
    protected TaskExecutionTable taskExecutionTable = new TaskExecutionTable();
    protected Map<UUID, Event> events = new HashMap<>();
    protected Iterator<UUID> currentTaskIterator;
    protected UUID currentScheduleId;
    protected TimeSynchronizerInterface timeSynchronizer;

    public SimulationScheduler(TimeSynchronizerInterface timeSynchronizer) {
        this.timeSynchronizer = timeSynchronizer;
    }

    @Override
    public UUID schedule(Runnable task, Schedule schedule) {
        UUID scheduleId = UUID.randomUUID();
        logger.info("Scheduling " + scheduleId + " with " + schedule);
        schedules.put(scheduleId, schedule);
        taskExecutionTable.registerTask(scheduleId, task);
        registerEvent(scheduleId, eventFromSchedule(schedule));
        return scheduleId;
    }

    @Override
    public void stop(UUID scheduleId, boolean mayInterruptRunning) {
        logger.info("Stopping " + scheduleId);
        unregisterEvent(scheduleId);
        schedules.remove(scheduleId);
    }

    @Override
    public void tick() {
        Instant time = timeSynchronizer.currentTime();
        if (taskExecutionTable.hasPlannedExecutions(time)) {
            List<UUID> invokedSchedulesIds = new ArrayList<>();
            currentTaskIterator = taskExecutionTable.getPlannedExecutions(time).iterator();

            while (currentTaskIterator.hasNext()) {
                currentScheduleId = currentTaskIterator.next();
                Runnable task = taskExecutionTable.getTask(currentScheduleId);
                Event event = events.get(currentScheduleId);

                if (event.getTimePoint().equals(time)) {
                    unregisterEvent(currentScheduleId);
                    Schedule schedule = schedules.get(currentScheduleId);
                    task.run();
                    schedule.incrementTotalCalls();
                    invokedSchedulesIds.add(currentScheduleId);
                }
            }

            currentScheduleId = null;
            currentTaskIterator = null;
            registerNewInvokeEvents(invokedSchedulesIds);
            taskExecutionTable.finishPlannedExecutions(time);
        }
    }

    public void registerNewInvokeEvents(List<UUID> scheduleIds) {
        for (UUID scheduleId : scheduleIds) {
            if (schedules.containsKey(scheduleId) && !events.containsKey(scheduleId)) {
                Schedule schedule = schedules.get(scheduleId);
                registerEvent(scheduleId, eventFromSchedule(schedule));
            }
        }
    }

    @Override
    public void stopAll() {
        for (UUID scheduleId : events.keySet()) {
            unregisterEvent(scheduleId);
        }
    }

    @Override
    public void observeEventsBy(EventObserver observer) {
        this.eventObserver = observer;
    }

    protected Event eventFromSchedule(Schedule schedule) {
        if (schedule.getTotalCalls() == 0) {
            return Event.create(schedule.getStartTime(), this);
        } else if (schedule.getExecutionType() == Schedule.ExecutionType.DEFAULT) {
            return null;
        } else {
            Instant startTime = schedule.getStartTime();
            Instant scheduleCallTime = startTime.plus(schedule.getInterval().multipliedBy(schedule.getTotalCalls()));
            return Event.create(scheduleCallTime, this);
        }
    }

    protected void registerEvent(UUID scheduleId, Event event) {
        if (event == null || events.containsKey(scheduleId)) {
            return;
        }

        events.put(scheduleId, event);
        taskExecutionTable.planExecution(event.getTimePoint(), scheduleId);
        eventObserver.scheduleEvent(event);
    }

    protected void unregisterEvent(UUID scheduleId) {
        if (!events.containsKey(scheduleId)) {
            return;
        }

        Event event = events.get(scheduleId);
        eventObserver.cancelEvent(event);

        if (currentTaskIterator != null && currentScheduleId.equals(scheduleId)) {
            currentTaskIterator.remove();
        } else {
            taskExecutionTable.cancelExecution(scheduleId);
        }

        events.remove(scheduleId);
    }
}
