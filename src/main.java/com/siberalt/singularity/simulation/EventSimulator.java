package com.siberalt.singularity.simulation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class EventSimulator {
    protected EventObserver eventObserver;

    protected List<TimeDependentUnitInterface> timeDependentUnits = new ArrayList<>();

    protected SimulationTimeSynchronizerInterface timeSynchronizer;

    public EventSimulator(EventObserver eventObserver, SimulationTimeSynchronizerInterface timeSynchronizer) {
        this.eventObserver = eventObserver;
        this.timeSynchronizer = timeSynchronizer;
    }

    public EventSimulator addTimeDependentUnit(TimeDependentUnitInterface timeDependentUnit) {
        this.timeDependentUnits.add(timeDependentUnit);

        return this;
    }

    public void run(Instant from, Instant to) {
        var currentTime = from;

        while (currentTime.isBefore(to)) {
            timeSynchronizer.syncCurrentTime(currentTime);

            for (var timeDependentUnit : timeDependentUnits) {
                timeDependentUnit.tick();
            }

            if (!eventObserver.hasUpcomingEvents()) {
                break;
            }

            currentTime = eventObserver.getNextEventTime();
            eventObserver.advanceToNextEvent();
        }
    }
}
