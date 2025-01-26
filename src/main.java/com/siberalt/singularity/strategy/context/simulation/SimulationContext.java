package com.siberalt.singularity.strategy.context.simulation;

import com.siberalt.singularity.strategy.context.AbstractContext;
import com.siberalt.singularity.strategy.scheduler.SchedulerInterface;
import com.siberalt.singularity.broker.container.AbstractBrokerContainer;
import com.siberalt.singularity.broker.contract.simulation.SimulationBrokerInterface;
import com.siberalt.singularity.strategy.context.TimeSynchronizerInterface;

public class SimulationContext extends AbstractContext<SimulationBrokerInterface> {
    public SimulationContext(SchedulerInterface scheduler, AbstractBrokerContainer<SimulationBrokerInterface> brokers, TimeSynchronizerInterface timeSynchronizer) {
        super(scheduler, brokers, timeSynchronizer);
    }
}
