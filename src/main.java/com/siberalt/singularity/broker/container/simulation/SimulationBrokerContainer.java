package com.siberalt.singularity.broker.container.simulation;

import com.siberalt.singularity.broker.container.AbstractBrokerContainer;
import com.siberalt.singularity.broker.contract.simulation.SimulationBrokerInterface;

import java.util.Map;

public class SimulationBrokerContainer extends AbstractBrokerContainer<SimulationBrokerInterface> {
    public SimulationBrokerContainer(Map<String, SimulationBrokerInterface> brokers) {
        super(brokers);
    }

    public SimulationBrokerContainer(Map<String, SimulationBrokerInterface> brokers, String defaultBrokerId) {
        super(brokers, defaultBrokerId);
    }
}
