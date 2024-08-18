package investtech.strategy;

import investtech.broker.container.emulation.EmulationBrokerContainer;
import investtech.broker.contract.emulation.EmulationBrokerInterface;
import investtech.emulation.EventInvokerInterface;
import investtech.emulation.EventObserver;
import investtech.emulation.TimeDependentUnitInterface;
import investtech.emulation.TimeFlowEmulator;
import investtech.strategy.context.emulation.EmulationContext;
import investtech.strategy.context.emulation.time.EmulationTimeSynchronizer;
import investtech.strategy.scheduler.emulation.EmulationSchedulerInterface;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class StrategyEmulator {
    protected Map<String, StrategyInterface> strategies;
    protected Map<String, EmulationBrokerInterface> brokers;
    protected EmulationSchedulerInterface scheduler;
    protected EmulationContext context;
    protected boolean isRunning = false;
    protected TimeFlowEmulator timeFlowEmulator;

    public StrategyEmulator(
            Map<String, StrategyInterface> strategies,
            Map<String, EmulationBrokerInterface> brokers,
            EmulationSchedulerInterface scheduler
    ) {
        this.strategies = strategies;
        this.brokers = brokers;
        this.scheduler = scheduler;
    }

    public void run(Instant from, Instant to) {
        isRunning = true;
        EmulationTimeSynchronizer timeSynchronizer = new EmulationTimeSynchronizer();
        EventObserver eventObserver = new EventObserver();
        timeFlowEmulator = new TimeFlowEmulator(eventObserver, timeSynchronizer);

        for (var broker : this.brokers.values()) {
            broker.initPeriod(from, to);

            if (broker.getEventManager() instanceof EventInvokerInterface) {
                ((EventInvokerInterface) broker.getEventManager()).observeEventsBy(eventObserver);
                timeFlowEmulator.addTimeDependentUnit((TimeDependentUnitInterface) broker.getEventManager());
            }
        }

        if (scheduler instanceof EventInvokerInterface) {
            ((EventInvokerInterface) scheduler).observeEventsBy(eventObserver);
            timeFlowEmulator.addTimeDependentUnit((TimeDependentUnitInterface) scheduler);
        }

        var context = createContext(timeSynchronizer);

        for (StrategyInterface strategy : strategies.values()) {
            strategy.start(context);
        }

        timeFlowEmulator.run(from, to);
        isRunning = false;
    }

    protected EmulationContext createContext(EmulationTimeSynchronizer timeSynchronizer) {
        context = new EmulationContext(
                this.scheduler,
                new EmulationBrokerContainer(this.brokers),
                timeSynchronizer
        );

        return context;
    }

}
