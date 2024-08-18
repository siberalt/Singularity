package investtech.broker.container.emulation;

import investtech.broker.container.AbstractBrokerContainer;
import investtech.broker.contract.emulation.EmulationBrokerInterface;

import java.util.Map;

public class EmulationBrokerContainer extends AbstractBrokerContainer<EmulationBrokerInterface> {
    public EmulationBrokerContainer(Map<String, EmulationBrokerInterface> brokers) {
        super(brokers);
    }

    public EmulationBrokerContainer(Map<String, EmulationBrokerInterface> brokers, String defaultBrokerId) {
        super(brokers, defaultBrokerId);
    }
}
