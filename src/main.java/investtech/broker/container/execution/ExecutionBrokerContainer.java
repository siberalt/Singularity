package investtech.broker.container.execution;

import investtech.broker.container.AbstractBrokerContainer;
import investtech.broker.contract.execution.BrokerInterface;

import java.util.Map;

public class ExecutionBrokerContainer extends AbstractBrokerContainer<BrokerInterface> {
    public ExecutionBrokerContainer(Map<String, BrokerInterface> brokers) {
        super(brokers);
    }

    public ExecutionBrokerContainer(Map<String, BrokerInterface> brokers, String defaultBrokerId) {
        super(brokers, defaultBrokerId);
    }
}
