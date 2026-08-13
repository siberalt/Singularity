package com.siberalt.singularity.strategy.simulation.runner;

import com.siberalt.singularity.broker.contract.service.user.Account;
import com.siberalt.singularity.shared.TimeRange;
import com.siberalt.singularity.simulation.EventSimulator;

public interface EventSimulatorInitializer {
    void initialize(TimeRange timeRange, Account account, EventSimulator simulator);
}
