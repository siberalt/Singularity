package investtech.strategy.context;

import investtech.broker.container.AbstractBrokerContainer;
import investtech.broker.contract.execution.BrokerInterface;
import investtech.strategy.scheduler.SchedulerInterface;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public abstract class AbstractContext<brokerT extends BrokerInterface> {
    private final AbstractBrokerContainer<brokerT> brokers;
    private final SchedulerInterface scheduler;
    protected final TimeSynchronizerInterface timeSynchronizer;
    protected Map<String, Object> params = new HashMap<>();

    public AbstractContext(
            SchedulerInterface scheduler,
            AbstractBrokerContainer<brokerT> brokers,
            TimeSynchronizerInterface timeSynchronizer
    ) {
        this.scheduler = scheduler;
        this.brokers = brokers;
        this.timeSynchronizer = timeSynchronizer;
    }

    public SchedulerInterface getScheduler() {
        return scheduler;
    }

    public AbstractBrokerContainer<brokerT> getBrokerContainer() {
        return brokers;
    }

    public Instant getCurrentTime() {
        return timeSynchronizer.currentTime();
    }

    public Map<String, Object> getParams() {
        return params;
    }
}
