package com.siberalt.singularity.strategy.context.execution;

import com.siberalt.singularity.broker.container.AbstractBrokerContainer;
import com.siberalt.singularity.broker.contract.execution.BrokerInterface;
import com.siberalt.singularity.strategy.context.AbstractContext;
import com.siberalt.singularity.strategy.context.TimeSynchronizerInterface;
import com.siberalt.singularity.strategy.scheduler.SchedulerInterface;

public class ExecutionContext extends AbstractContext<BrokerInterface> {
    public ExecutionContext(
            SchedulerInterface scheduler,
            AbstractBrokerContainer<BrokerInterface> brokers,
            TimeSynchronizerInterface timeSynchronizer) {
        super(scheduler, brokers, timeSynchronizer);
    }
}
