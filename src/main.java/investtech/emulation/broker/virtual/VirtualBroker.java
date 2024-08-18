package investtech.emulation.broker.virtual;

import investtech.broker.contract.emulation.EmulationBrokerInterface;
import investtech.broker.contract.run.StopOrderServiceAwareBrokerInterface;
import investtech.broker.contract.service.instrument.InstrumentServiceInterface;
import investtech.broker.contract.service.market.MarketDataServiceInterface;
import investtech.broker.contract.service.operation.OperationsServiceInterface;
import investtech.broker.contract.service.order.OrderServiceInterface;
import investtech.broker.contract.service.order.stop.StopOrderServiceInterface;
import investtech.broker.contract.service.user.UserServiceInterface;
import investtech.emulation.shared.market.candle.CandleStorageInterface;
import investtech.strategy.context.AbstractContext;
import investtech.strategy.context.ContextAwareInterface;
import investtech.strategy.event.EventManagerInterface;

import java.time.Instant;

public class VirtualBroker implements EmulationBrokerInterface, StopOrderServiceAwareBrokerInterface, ContextAwareInterface {
    protected CandleStorageInterface candleStorage;

    protected Instant toTime;

    protected AbstractContext<?> context;

    public VirtualBroker(CandleStorageInterface candleRepository) {
        this.candleStorage = candleRepository;
    }

    @Override
    public void initPeriod(Instant from, Instant to) {
        this.candleStorage.rewindTo(from);
        this.toTime = to;
    }

    @Override
    public InstrumentServiceInterface getInstrumentService() {
        return null;
    }

    @Override
    public MarketDataServiceInterface getMarketDataService() {
        return null;
    }

    @Override
    public OperationsServiceInterface getOperationsService() {
        return null;
    }

    @Override
    public OrderServiceInterface getOrderService() {
        return null;
    }

    @Override
    public UserServiceInterface getUserService() {
        return null;
    }

    @Override
    public EventManagerInterface getEventManager() {
        return null;
    }

    @Override
    public StopOrderServiceInterface getStopOrderService() {
        return null;
    }

    @Override
    public void applyContext(AbstractContext<?> context) {
        this.context = context;
    }
}
