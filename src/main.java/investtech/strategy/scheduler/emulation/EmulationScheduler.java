package investtech.strategy.scheduler.emulation;

import investtech.emulation.Event;
import investtech.emulation.EventInvokerInterface;
import investtech.emulation.EventObserver;
import investtech.strategy.StrategyInterface;
import investtech.strategy.context.AbstractContext;
import investtech.strategy.scheduler.Schedule;

import java.time.Instant;
import java.util.*;

public class EmulationScheduler implements EmulationSchedulerInterface, EventInvokerInterface {
    protected EventObserver eventObserver;

    protected AbstractContext<?> context;
    protected Map<String, Schedule> strategySchedules = new HashMap<>();
    protected SortedMap<Instant, SortedMap<String, StrategyInterface>> callTimePoints = new TreeMap<>();
    protected Map<String, Event> strategyEvents = new HashMap<>();

    protected Iterator<Map.Entry<String, StrategyInterface>> currentCallTimePointsIterator = null;

    protected Map.Entry<String, StrategyInterface> currentRunningStrategy = null;

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
        var time = context.getCurrentTime();

        if (callTimePoints.containsKey(time)) {
            var registeringNewEventsForStrategies = new ArrayList<StrategyInterface>();
            currentCallTimePointsIterator = callTimePoints.get(time).entrySet().iterator();

            while (currentCallTimePointsIterator.hasNext()) {
                currentRunningStrategy = currentCallTimePointsIterator.next();
                var strategy = currentRunningStrategy.getValue();
                var event = strategyEvents.get(strategy.getId());

                if (event.getTimePoint().equals(time)) {
                    unregisterStrategyEvent(strategy.getId());
                    var schedule = strategySchedules.get(strategy.getId());
                    strategy.run(context);
                    schedule.incrementTotalCalls();

                    registeringNewEventsForStrategies.add(strategy);
                }
            }

            currentRunningStrategy = null;
            currentCallTimePointsIterator = null;

            registerNewStrategiesEvents(registeringNewEventsForStrategies);

            callTimePoints.remove(time);
        }
    }

    public void registerNewStrategiesEvents(Collection<StrategyInterface> strategies) {
        for (var strategy : strategies) {
            if (strategySchedules.containsKey(strategy.getId()) && !strategyEvents.containsKey(strategy.getId())) {
                var schedule = strategySchedules.get(strategy.getId());
                registerStrategyEvent(strategy, eventFromSchedule(schedule));
            }
        }
    }

    @Override
    public void stopAll() {
        for (var strategyId : strategyEvents.keySet()) {
            unregisterStrategyEvent(strategyId);
        }
    }

    @Override
    public void observeEventsBy(EventObserver observer) {
        this.eventObserver = observer;
    }

    protected Event eventFromSchedule(Schedule schedule) {
        Event event;

        if (0 == schedule.getTotalCalls()) {
            event = Event.create(schedule.getStartTime(), this);
        } else if (Schedule.ExecutionType.DEFAULT == schedule.getExecutionType()) {
            event = null;
        } else {
            var startTime = schedule.getStartTime();
            var interval = schedule.getInterval();
            var totalCalls = schedule.getTotalCalls();
            var scheduleCallTime = startTime.plus(interval.multipliedBy(totalCalls));
            event = Event.create(scheduleCallTime, this);
        }

        return event;
    }

    protected void registerStrategyEvent(StrategyInterface strategy, Event event) {
        if (null == event || strategyEvents.containsKey(strategy.getId())) {
            return;
        }

        strategyEvents.put(strategy.getId(), event);
        callTimePoints.computeIfAbsent(
                event.getTimePoint(), x -> new TreeMap<>()
        ).put(event.getId(), strategy);
        eventObserver.plan(event);
    }

    protected void unregisterStrategyEvent(String strategyId) {
        if (!strategyEvents.containsKey(strategyId)) {
            return;
        }

        var event = strategyEvents.get(strategyId);
        eventObserver.cancel(event);

        if (null != currentCallTimePointsIterator && currentRunningStrategy.getValue().getId().equals(strategyId)) {
            currentCallTimePointsIterator.remove();
        } else {
            for (Map<String, StrategyInterface> strategyMap : callTimePoints.values()) {
                strategyMap.remove(event.getId());
            }
        }

        strategyEvents.remove(strategyId);
    }
}
