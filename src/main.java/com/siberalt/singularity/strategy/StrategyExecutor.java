package com.siberalt.singularity.strategy;

import com.siberalt.singularity.broker.contract.execution.BrokerInterface;
import com.siberalt.singularity.strategy.scheduler.SchedulerInterface;
import com.siberalt.singularity.broker.container.execution.ExecutionBrokerContainer;
import com.siberalt.singularity.strategy.context.execution.ExecutionContext;
import com.siberalt.singularity.strategy.context.execution.time.TimeSynchronizer;

import java.util.Map;

public class StrategyExecutor {
    protected Map<String, StrategyInterface> strategies;
    protected Map<String, BrokerInterface> brokers;
    protected SchedulerInterface scheduler;
    protected ExecutionContext context;
    protected boolean isRunning = false;

    public StrategyExecutor(
            Map<String, StrategyInterface> strategies,
            Map<String, BrokerInterface> brokers,
            SchedulerInterface scheduler
    ) {
        this.strategies = strategies;
        this.brokers = brokers;
        this.scheduler = scheduler;
    }

    public void run() {
        if (isRunning) {
            // TODO throw exception
        }

        isRunning = true;
        context = createContext();

        for (StrategyInterface strategy : strategies.values()) {
            strategy.start(context);
        }
    }

    public void stop() {
        if (!isRunning) {
            // TODO throw exception
        }

        isRunning = false;

        for (StrategyInterface strategy : strategies.values()) {
            strategy.stop(context);
        }
    }

    public boolean isRunning() {
        return isRunning;
    }

    protected ExecutionContext createContext() {
        context = new ExecutionContext(
                this.scheduler,
                new ExecutionBrokerContainer(this.brokers),
                new TimeSynchronizer()
        );

        return context;
    }
}
