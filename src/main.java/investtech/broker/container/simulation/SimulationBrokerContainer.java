package investtech.broker.container.simulation;

import investtech.broker.container.AbstractBrokerContainer;
import investtech.broker.contract.simulation.SimulationBrokerInterface;

import java.util.Map;

public class SimulationBrokerContainer extends AbstractBrokerContainer<SimulationBrokerInterface> {
    public SimulationBrokerContainer(Map<String, SimulationBrokerInterface> brokers) {
        super(brokers);
    }

    public SimulationBrokerContainer(Map<String, SimulationBrokerInterface> brokers, String defaultBrokerId) {
        super(brokers, defaultBrokerId);
    }
}
