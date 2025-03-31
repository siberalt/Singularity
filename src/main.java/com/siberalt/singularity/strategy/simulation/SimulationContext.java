package com.siberalt.singularity.strategy.simulation;

import com.siberalt.singularity.strategy.context.AbstractContext;
import com.siberalt.singularity.scheduler.SchedulerInterface;
import com.siberalt.singularity.broker.container.AbstractBrokerContainer;
import com.siberalt.singularity.broker.contract.simulation.SimulationBrokerInterface;
import com.siberalt.singularity.strategy.context.Clock;

public class SimulationContext extends AbstractContext<SimulationBrokerInterface> {
    public SimulationContext(SchedulerInterface scheduler, AbstractBrokerContainer<SimulationBrokerInterface> brokers, Clock timeSynchronizer) {
        super(scheduler, brokers, timeSynchronizer);
    }
}
