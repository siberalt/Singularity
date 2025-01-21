package investtech.strategy;

import investtech.broker.container.simulation.SimulationBrokerContainer;
import investtech.broker.contract.simulation.SimulationBrokerInterface;
import investtech.simulation.EventInvokerInterface;
import investtech.simulation.EventObserver;
import investtech.simulation.TimeDependentUnitInterface;
import investtech.simulation.Simulator;
import investtech.strategy.context.emulation.SimulationContext;
import investtech.strategy.context.emulation.time.SimulationTimeSynchronizer;
import investtech.strategy.scheduler.emulation.EmulationSchedulerInterface;

import java.time.Instant;
import java.util.Map;

public class StrategySimulator {
    protected Map<String, StrategyInterface> strategies;
    protected Map<String, SimulationBrokerInterface> brokers;
    protected EmulationSchedulerInterface scheduler;
    protected SimulationContext context;
    protected boolean isRunning = false;
    protected Simulator simulator;

    public StrategySimulator(
            Map<String, StrategyInterface> strategies,
            Map<String, SimulationBrokerInterface> brokers,
            EmulationSchedulerInterface scheduler
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
