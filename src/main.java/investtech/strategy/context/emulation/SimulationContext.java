package investtech.strategy.context.emulation;

import investtech.broker.container.AbstractBrokerContainer;
import investtech.broker.contract.simulation.SimulationBrokerInterface;
import investtech.strategy.context.AbstractContext;
import investtech.strategy.context.TimeSynchronizerInterface;
import investtech.strategy.scheduler.SchedulerInterface;

public class SimulationContext extends AbstractContext<SimulationBrokerInterface> {
    public SimulationContext(SchedulerInterface scheduler, AbstractBrokerContainer<SimulationBrokerInterface> brokers, TimeSynchronizerInterface timeSynchronizer) {
        super(scheduler, brokers, timeSynchronizer);
    }
}
