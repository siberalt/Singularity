package com.siberalt.singularity.simulation;

import com.siberalt.singularity.simulation.synch.Synchronizable;
import com.siberalt.singularity.simulation.synch.TaskSynchronizer;
import com.siberalt.singularity.simulation.time.SimpleSimulationClock;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class EventSimulator {
    private final EventObserver eventObserver;
    private final List<TimeDependentUnit> timeDependentUnits = new ArrayList<>();
    private final SimulationClock clock;
    private final List<Initializable> initializableUnits = new ArrayList<>();
    private final List<EventInvoker> eventInvokers = new ArrayList<>();
    private final List<Synchronizable> synchronizableUnits = new ArrayList<>();
    private final TaskSynchronizer synchronizer = new TaskSynchronizer(Thread.currentThread());

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

    public void addEventInvoker(EventInvoker eventInvoker) {
        eventInvokers.add(eventInvoker);
    }

    public void addInitializableUnit(Initializable initializableUnit) {
        this.initializableUnits.add(initializableUnit);
    }

    public void addTimeDependentUnit(TimeDependentUnit timeDependentUnit) {
        this.timeDependentUnits.add(timeDependentUnit);
    }

    public void addSynchronizableUnit(Synchronizable synchronizableUnit) {
        this.synchronizableUnits.add(synchronizableUnit);
    }

    public void addSimulationUnit(SimulationUnit simulationUnit) {
        if (simulationUnit instanceof TimeDependentUnit timeDependentUnit) {
            addTimeDependentUnit(timeDependentUnit);
        }
        if (simulationUnit instanceof Initializable initializableUnit) {
            addInitializableUnit(initializableUnit);
        }
        if (simulationUnit instanceof EventInvoker eventInvoker) {
            addEventInvoker(eventInvoker);
        }
        if (simulationUnit instanceof Synchronizable synchronizableUnit) {
            addSynchronizableUnit(synchronizableUnit);
        }
    }

    public void addTask(Runnable task) {
        synchronizer.registerTask(task);
    }

    public void ignoreThreadUncaughtExceptions(boolean ignore) {
        synchronizer.setIgnoreUncaughtExceptions(ignore);
    }

    public void run(Instant from, Instant to) {
        var currentTime = from;
        clock.syncCurrentTime(currentTime);

        init(from, to);
        synchronizer.start();
        synchronizer.waitForTasks();

        while (currentTime.isBefore(to) || currentTime.equals(to)) {
            clock.syncCurrentTime(currentTime);

            for (var timeDependentUnit : timeDependentUnits) {
                timeDependentUnit.tick();
            }

            synchronizer.waitForTasks();

            if (!eventObserver.hasUpcomingEvents()) {
                System.out.println("No more events to process. Ending simulation.");
                break;
            }

            currentTime = eventObserver.getNextEventTime();
            eventObserver.advanceToNextEvent();
        }
    }

    protected void init(Instant startTime, Instant endTime) {
        synchronizableUnits.forEach(synchronizableUnit -> synchronizableUnit.synchWith(synchronizer));
        timeDependentUnits.forEach(timeDependentUnit -> timeDependentUnit.applyClock(clock));
        eventInvokers.forEach(eventInvoker -> eventInvoker.observeEventsBy(eventObserver));
        initializableUnits.forEach(initializableUnit -> initializableUnit.init(startTime, endTime));
    }
}
