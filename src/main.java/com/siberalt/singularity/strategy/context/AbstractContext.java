package com.siberalt.singularity.strategy.context;

import com.siberalt.singularity.scheduler.Scheduler;
import com.siberalt.singularity.broker.container.AbstractBrokerContainer;
import com.siberalt.singularity.broker.contract.execution.Broker;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

// TODO improve and fix
public abstract class AbstractContext<brokerT extends Broker> {
    private final AbstractBrokerContainer<brokerT> brokers;
    private final Scheduler scheduler;
    protected final Clock timeSynchronizer;
    protected Map<String, Object> params = new HashMap<>();

    public AbstractContext(
            Scheduler scheduler,
            AbstractBrokerContainer<brokerT> brokers,
            Clock timeSynchronizer
    ) {
        this.scheduler = scheduler;
        this.brokers = brokers;
        this.timeSynchronizer = timeSynchronizer;
    }

    public Scheduler getScheduler() {
        return scheduler;
    }

    public AbstractBrokerContainer<brokerT> getBrokerContainer() {
        return brokers;
    }

    public Instant getCurrentTime() {
        return timeSynchronizer.currentTime();
    }

    public Map<String, Object> getParams() {
        return params;
    }
}
