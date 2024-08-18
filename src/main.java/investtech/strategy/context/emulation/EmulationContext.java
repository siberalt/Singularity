package investtech.strategy.context.emulation;

import investtech.broker.container.AbstractBrokerContainer;
import investtech.broker.contract.emulation.EmulationBrokerInterface;
import investtech.strategy.context.AbstractContext;
import investtech.strategy.context.TimeSynchronizerInterface;
import investtech.strategy.scheduler.SchedulerInterface;

public class EmulationContext extends AbstractContext<EmulationBrokerInterface> {
    public EmulationContext(SchedulerInterface scheduler, AbstractBrokerContainer<EmulationBrokerInterface> brokers, TimeSynchronizerInterface timeSynchronizer) {
        super(scheduler, brokers, timeSynchronizer);
    }
}
