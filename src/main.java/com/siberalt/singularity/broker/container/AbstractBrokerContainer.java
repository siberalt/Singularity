package com.siberalt.singularity.broker.container;

import com.siberalt.singularity.broker.contract.execution.BrokerInterface;

import java.util.Map;

public abstract class AbstractBrokerContainer<brokerT extends BrokerInterface> {
    protected Map<String, brokerT> brokers;

    protected String defaultBrokerId;

    public AbstractBrokerContainer(Map<String, brokerT> brokers) {
        this.brokers = brokers;
    }

    public AbstractBrokerContainer(Map<String, brokerT> brokers, String defaultBrokerId) {
        this.brokers = brokers;
        this.defaultBrokerId = defaultBrokerId;
    }

    public brokerT getDefault() {
        return get(defaultBrokerId);
    }

    public brokerT get(String brokerId) {
        return brokers.getOrDefault(brokerId, null);
    }
}
