package com.siberalt.singularity.strategy.simulation;

import com.siberalt.singularity.simulation.*;
import com.siberalt.singularity.strategy.context.Clock;

import java.time.Instant;
import java.util.Comparator;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Consumer;

public class UserActionSimulator<T> implements TimeDependentUnitInterface, EventInvokerInterface, Initializable {
    private final T userContext;
    private EventObserver eventObserver;
    private final SortedMap<Instant, Consumer<T>> actions = new TreeMap<>(Comparator.naturalOrder());
    private Clock clock;

    public UserActionSimulator(T userContext) {
        this.userContext = userContext;
    }

    public void planAction(String time, Consumer<T> action) {
        actions.put(Instant.parse(time), action);
    }

    public void planAction(Instant time, Consumer<T> action) {
        actions.put(time, action);
    }

    public void cancelAction(Instant time) {
        actions.remove(time);
    }

    @Override
    public void applyClock(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void tick() {
        Instant time = clock.currentTime();

        if (actions.containsKey(time)) {
            actions.get(time).accept(this.userContext);
            actions.remove(time);

            if (!actions.isEmpty()) {
                eventObserver.scheduleEvent(Event.create(actions.firstKey(), this));
            }
        }
    }

    @Override
    public void observeEventsBy(EventObserver observer) {
        this.eventObserver = observer;
    }

    @Override
    public void init(Instant startTime, Instant endTime) {
        for (var entry : actions.entrySet()) {
            if (entry.getKey().isBefore(startTime) || entry.getKey().isAfter(endTime)) {
                throw new IllegalStateException("Action is planned outside of the simulation time frame");
            }
        }

        eventObserver.scheduleEvent(Event.create(actions.firstKey(), this));
    }
}
