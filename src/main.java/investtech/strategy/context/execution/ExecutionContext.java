package investtech.strategy.context.execution;

import investtech.broker.container.AbstractBrokerContainer;
import investtech.broker.contract.execution.BrokerInterface;
import investtech.strategy.context.AbstractContext;
import investtech.strategy.context.TimeSynchronizerInterface;
import investtech.strategy.scheduler.SchedulerInterface;

public class ExecutionContext extends AbstractContext<BrokerInterface> {
    public ExecutionContext(
            SchedulerInterface scheduler,
            AbstractBrokerContainer<BrokerInterface> brokers,
            TimeSynchronizerInterface timeSynchronizer) {
        super(scheduler, brokers, timeSynchronizer);
    }
}
