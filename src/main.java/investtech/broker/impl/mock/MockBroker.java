package investtech.broker.impl.mock;

import investtech.broker.contract.execution.StopOrderServiceAwareBrokerInterface;
import investtech.broker.contract.service.order.stop.StopOrderServiceInterface;
import investtech.simulation.shared.instrument.InstrumentStorageInterface;
import investtech.simulation.shared.market.candle.CandleStorageInterface;
import investtech.strategy.context.AbstractContext;
import investtech.strategy.context.ContextAwareInterface;
import investtech.strategy.event.EventManagerInterface;

public class MockBroker implements StopOrderServiceAwareBrokerInterface, ContextAwareInterface {
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
    public MockInstrumentService getInstrumentService() {
        return instrumentService;
    }

    @Override
    public MockMarketDataService getMarketDataService() {
        return marketDataService;
    }

    @Override
    public MockOperationsService getOperationsService() {
        return operationsService;
    }

    @Override
    public MockOrderService getOrderService() {
        return orderService;
    }

    @Override
    public MockUserService getUserService() {
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
