package com.siberalt.singularity.strategy.simulation.runner;

import com.siberalt.singularity.broker.contract.service.user.Account;
import com.siberalt.singularity.shared.TimeRange;
import com.siberalt.singularity.strategy.observer.Observer;

public interface StrategyStarter {
    void start(TimeRange timeRange, Account account, Observer observer);
}
