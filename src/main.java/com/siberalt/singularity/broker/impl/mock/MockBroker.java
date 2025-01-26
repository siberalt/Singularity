package com.siberalt.singularity.broker.impl.mock;

import com.siberalt.singularity.broker.contract.execution.StopOrderServiceAwareBrokerInterface;
import com.siberalt.singularity.broker.contract.service.order.stop.StopOrderServiceInterface;
import com.siberalt.singularity.simulation.shared.instrument.InstrumentStorageInterface;
import com.siberalt.singularity.simulation.shared.market.candle.CandleStorageInterface;
import com.siberalt.singularity.strategy.context.AbstractContext;
import com.siberalt.singularity.strategy.context.ContextAwareInterface;
import com.siberalt.singularity.strategy.event.EventManagerInterface;

class MockBroker implements StopOrderServiceAwareBrokerInterface, ContextAwareInterface {
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
