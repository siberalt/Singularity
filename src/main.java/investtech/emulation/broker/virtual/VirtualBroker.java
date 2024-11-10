package investtech.emulation.broker.virtual;

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

public class VirtualBroker implements EmulationBrokerInterface, StopOrderServiceAwareBrokerInterface, ContextAwareInterface {
    protected Instant toTime;
    protected AbstractContext<?> context;
    protected VirtualMarketDataService marketDataService;
    protected VirtualOrderService orderService;
    protected VirtualOperationsService operationsService;
    protected VirtualInstrumentService instrumentService;
    protected VirtualUserService userService;

    public VirtualBroker(CandleStorageInterface candleRepository, InstrumentStorageInterface instrumentStorage) {
        orderService = new VirtualOrderService(this);
        marketDataService = new VirtualMarketDataService(this, candleRepository);
        operationsService = new VirtualOperationsService(this);
        instrumentService = new VirtualInstrumentService(this, instrumentStorage);
        userService = new VirtualUserService(this);
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
