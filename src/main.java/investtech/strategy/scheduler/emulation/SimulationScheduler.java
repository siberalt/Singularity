package investtech.strategy.scheduler.emulation;

import investtech.simulation.*;
import investtech.strategy.*;
import investtech.strategy.context.AbstractContext;
import investtech.strategy.scheduler.Schedule;

import java.time.Instant;
import java.util.*;

public class SimulationScheduler implements EmulationSchedulerInterface, EventInvokerInterface, TimeDependentUnitInterface {
    protected EventObserver eventObserver;
    protected AbstractContext<?> context;
    protected Map<String, Schedule> strategySchedules = new HashMap<>();
    protected SortedMap<Instant, SortedMap<String, StrategyInterface>> callTimePoints = new TreeMap<>();
    protected Map<String, Event> strategyEvents = new HashMap<>();
    protected Iterator<Map.Entry<String, StrategyInterface>> currentCallTimePointsIterator;
    protected Map.Entry<String, StrategyInterface> currentRunningStrategy;

    @Override
    public void applyContext(AbstractContext<?> context) {
        this.context = context;
    }

    @Override
    public void schedule(StrategyInterface strategy, Schedule schedule) {
        strategySchedules.put(strategy.getId(), schedule);
        registerStrategyEvent(strategy, eventFromSchedule(schedule));
    }

    @Override
    public void stop(StrategyInterface strategy, boolean mayInterruptRunning) {
        unregisterStrategyEvent(strategy.getId());
        strategySchedules.remove(strategy.getId());
    }

    @Override
    public void tick() {
        Instant time = context.getCurrentTime();
        if (callTimePoints.containsKey(time)) {
            List<StrategyInterface> newEvents = new ArrayList<>();
            currentCallTimePointsIterator = callTimePoints.get(time).entrySet().iterator();

            while (currentCallTimePointsIterator.hasNext()) {
                currentRunningStrategy = currentCallTimePointsIterator.next();
                StrategyInterface strategy = currentRunningStrategy.getValue();
                Event event = strategyEvents.get(strategy.getId());

                if (event.getTimePoint().equals(time)) {
                    unregisterStrategyEvent(strategy.getId());
                    Schedule schedule = strategySchedules.get(strategy.getId());
                    strategy.run(context);
                    schedule.incrementTotalCalls();
                    newEvents.add(strategy);
                }
            }

            currentRunningStrategy = null;
            currentCallTimePointsIterator = null;
            registerNewStrategiesEvents(newEvents);
            callTimePoints.remove(time);
        }
    }

    public void registerNewStrategiesEvents(Collection<StrategyInterface> strategies) {
        for (StrategyInterface strategy : strategies) {
            if (strategySchedules.containsKey(strategy.getId()) && !strategyEvents.containsKey(strategy.getId())) {
                Schedule schedule = strategySchedules.get(strategy.getId());
                registerStrategyEvent(strategy, eventFromSchedule(schedule));
            }
        }
    }

    @Override
    public void stopAll() {
        for (String strategyId : strategyEvents.keySet()) {
            unregisterStrategyEvent(strategyId);
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

    protected void registerStrategyEvent(StrategyInterface strategy, Event event) {
        if (event == null || strategyEvents.containsKey(strategy.getId())) {
            return;
        }

        strategyEvents.put(strategy.getId(), event);
        callTimePoints.computeIfAbsent(event.getTimePoint(), k -> new TreeMap<>()).put(event.getId(), strategy);
        eventObserver.plan(event);
    }

    protected void unregisterStrategyEvent(String strategyId) {
        if (!strategyEvents.containsKey(strategyId)) {
            return;
        }

        Event event = strategyEvents.get(strategyId);
        eventObserver.cancel(event);

        if (currentCallTimePointsIterator != null && currentRunningStrategy.getValue().getId().equals(strategyId)) {
            currentCallTimePointsIterator.remove();
        } else {
            for (SortedMap<String, StrategyInterface> strategyMap : callTimePoints.values()) {
                strategyMap.remove(event.getId());
            }
        }

        strategyEvents.remove(strategyId);
    }
}