package investtech.broker.contract.simulation;

import investtech.broker.contract.execution.BrokerInterface;

import java.time.Instant;

public interface SimulationBrokerInterface extends BrokerInterface {
    void initPeriod(Instant from, Instant to);
}
