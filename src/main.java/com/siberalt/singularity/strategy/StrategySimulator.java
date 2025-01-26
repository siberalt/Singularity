package com.siberalt.singularity.strategy;

import com.siberalt.singularity.broker.container.simulation.SimulationBrokerContainer;
import com.siberalt.singularity.broker.contract.simulation.SimulationBrokerInterface;
import com.siberalt.singularity.simulation.Simulator;
import com.siberalt.singularity.strategy.context.simulation.SimulationContext;
import com.siberalt.singularity.strategy.context.simulation.time.SimulationTimeSynchronizer;
import com.siberalt.singularity.strategy.scheduler.simulation.SimulationSchedulerInterface;
import com.siberalt.singularity.simulation.EventInvokerInterface;
import com.siberalt.singularity.simulation.EventObserver;
import com.siberalt.singularity.simulation.TimeDependentUnitInterface;

import java.time.Instant;
import java.util.Map;

public class StrategySimulator {
    protected Map<String, StrategyInterface> strategies;
    protected Map<String, SimulationBrokerInterface> brokers;
    protected SimulationSchedulerInterface scheduler;
    protected SimulationContext context;
    protected boolean isRunning = false;
    protected Simulator simulator;

    public StrategySimulator(
            Map<String, StrategyInterface> strategies,
            Map<String, SimulationBrokerInterface> brokers,
            SimulationSchedulerInterface scheduler
    ) {
        this.strategies = strategies;
        this.brokers = brokers;
        this.scheduler = scheduler;
    }

    public void run(Instant from, Instant to) {
        isRunning = true;
        SimulationTimeSynchronizer timeSynchronizer = new SimulationTimeSynchronizer();
        EventObserver eventObserver = new EventObserver();
        simulator = new Simulator(eventObserver, timeSynchronizer);

        for (var broker : this.brokers.values()) {
            broker.initPeriod(from, to);

            if (broker.getEventManager() instanceof EventInvokerInterface) {
                ((EventInvokerInterface) broker.getEventManager()).observeEventsBy(eventObserver);
                simulator.addTimeDependentUnit((TimeDependentUnitInterface) broker.getEventManager());
            }
        }

        if (scheduler instanceof EventInvokerInterface) {
            ((EventInvokerInterface) scheduler).observeEventsBy(eventObserver);
            simulator.addTimeDependentUnit((TimeDependentUnitInterface) scheduler);
        }

        var context = createContext(timeSynchronizer);

        for (StrategyInterface strategy : strategies.values()) {
            strategy.start(context);
        }

        simulator.run(from, to);
        isRunning = false;
    }

    protected SimulationContext createContext(SimulationTimeSynchronizer timeSynchronizer) {
        context = new SimulationContext(
                this.scheduler,
                new SimulationBrokerContainer(this.brokers),
                timeSynchronizer
        );

        return context;
    }

}
