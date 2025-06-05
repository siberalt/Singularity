package com.siberalt.singularity.broker.contract.simulation;

import com.siberalt.singularity.broker.contract.execution.Broker;

import java.time.Instant;

public interface SimulationBrokerInterface extends Broker {
    void initPeriod(Instant from, Instant to);
}
