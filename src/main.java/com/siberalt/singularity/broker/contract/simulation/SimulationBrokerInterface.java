package com.siberalt.singularity.broker.contract.simulation;

import com.siberalt.singularity.broker.contract.execution.BrokerInterface;

import java.time.Instant;

public interface SimulationBrokerInterface extends BrokerInterface {
    void initPeriod(Instant from, Instant to);
}
