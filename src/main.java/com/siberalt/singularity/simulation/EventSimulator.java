package com.siberalt.singularity.simulation;

import com.siberalt.singularity.simulation.time.SimpleSimulationClock;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class EventSimulator {
    protected EventObserver eventObserver;
    protected List<TimeDependentUnitInterface> timeDependentUnits = new ArrayList<>();
    protected SimulationClock clock;
    protected List<Initializable> initializableUnits = new ArrayList<>();
    private List<EventInvokerInterface> eventInvokers = new ArrayList<>();

    public EventSimulator(SimulationClock clock) {
        this.eventObserver = new EventObserver();
        this.clock = clock;
    }

    public EventSimulator() {
        this.eventObserver = new EventObserver();
        this.clock = new SimpleSimulationClock();
    }

    public EventSimulator(EventObserver eventObserver, SimulationClock clock) {
        this.eventObserver = eventObserver;
        this.clock = clock;
    }

    public void addEventInvoker(EventInvokerInterface eventInvoker) {
        eventInvokers.add(eventInvoker);
        eventInvoker.observeEventsBy(eventObserver);
    }

    public void addInitializableUnit(Initializable initializableUnit) {
        this.initializableUnits.add(initializableUnit);
    }

    public void addTimeDependentUnit(TimeDependentUnitInterface timeDependentUnit) {
        this.timeDependentUnits.add(timeDependentUnit);
    }

    public void run(Instant from, Instant to) {
        var currentTime = from;
        init(from, to);

        while (currentTime.isBefore(to) || currentTime.equals(to)) {
            clock.syncCurrentTime(currentTime);

            for (var timeDependentUnit : timeDependentUnits) {
                timeDependentUnit.tick(clock);
            }

            if (!eventObserver.hasUpcomingEvents()) {
                break;
            }

            currentTime = eventObserver.getNextEventTime();
            eventObserver.advanceToNextEvent();
        }
    }

    protected void init(Instant startTime, Instant endTime) {
        for (var timeDependentUnit : timeDependentUnits) {
            if (timeDependentUnit instanceof Initializable) {
                ((Initializable) timeDependentUnit).init(startTime, endTime);
            }
        }
    }
}
