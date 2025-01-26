package com.siberalt.singularity.broker.container.execution;

import com.siberalt.singularity.broker.container.AbstractBrokerContainer;
import com.siberalt.singularity.broker.contract.execution.BrokerInterface;

import java.util.Map;

public class ExecutionBrokerContainer extends AbstractBrokerContainer<BrokerInterface> {
    public ExecutionBrokerContainer(Map<String, BrokerInterface> brokers) {
        super(brokers);
    }

    public ExecutionBrokerContainer(Map<String, BrokerInterface> brokers, String defaultBrokerId) {
        super(brokers, defaultBrokerId);
    }
}
