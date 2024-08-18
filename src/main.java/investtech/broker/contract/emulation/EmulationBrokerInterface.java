package investtech.broker.contract.emulation;

import investtech.broker.contract.run.BrokerInterface;

import java.time.Instant;

public interface EmulationBrokerInterface extends BrokerInterface {
    void initPeriod(Instant from, Instant to);
}
