package investtech.emulation.broker.mock;

import investtech.broker.contract.emulation.EmulationBrokerInterface;
import investtech.broker.contract.run.StopOrderServiceAwareBrokerInterface;
import investtech.broker.contract.service.instrument.InstrumentServiceInterface;
import investtech.broker.contract.service.market.MarketDataServiceInterface;
import investtech.broker.contract.service.operation.OperationsServiceInterface;
import investtech.broker.contract.service.order.OrderServiceInterface;
import investtech.broker.contract.service.order.stop.StopOrderServiceInterface;
import investtech.broker.contract.service.user.UserServiceInterface;
import investtech.emulation.shared.instrument.InstrumentStorageInterface;
import investtech.emulation.shared.market.candle.CandleStorageInterface;
import investtech.strategy.context.AbstractContext;
import investtech.strategy.context.ContextAwareInterface;
import investtech.strategy.event.EventManagerInterface;

import java.time.Instant;

public class MockBroker implements EmulationBrokerInterface, StopOrderServiceAwareBrokerInterface, ContextAwareInterface {
    protected Instant toTime;
    protected AbstractContext<?> context;
    protected MockMarketDataService marketDataService;
    protected MockOrderService orderService;
    protected MockOperationsService operationsService;
    protected MockInstrumentService instrumentService;
    protected MockUserService userService;

    public MockBroker(CandleStorageInterface candleRepository, InstrumentStorageInterface instrumentStorage) {
        orderService = new MockOrderService(this);
        marketDataService = new MockMarketDataService(this, candleRepository);
        operationsService = new MockOperationsService(this);
        instrumentService = new MockInstrumentService(this, instrumentStorage);
        userService = new MockUserService(this);
    }

    @Override
    public void initPeriod(Instant from, Instant to) {
        this.toTime = to;
    }

    @Override
    public InstrumentServiceInterface getInstrumentService() {
        return instrumentService;
    }

    @Override
    public MarketDataServiceInterface getMarketDataService() {
        return marketDataService;
    }

    @Override
    public OperationsServiceInterface getOperationsService() {
        return operationsService;
    }

    @Override
    public OrderServiceInterface getOrderService() {
        return orderService;
    }

    @Override
    public UserServiceInterface getUserService() {
        return userService;
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
