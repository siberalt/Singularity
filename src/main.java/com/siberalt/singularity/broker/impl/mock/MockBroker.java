package com.siberalt.singularity.broker.impl.mock;

import com.siberalt.singularity.broker.contract.execution.SandboxServiceAwareBroker;
import com.siberalt.singularity.broker.contract.execution.StopOrderServiceAwareBrokerInterface;
import com.siberalt.singularity.broker.contract.service.order.stop.StopOrderServiceInterface;
import com.siberalt.singularity.broker.contract.service.sandbox.SandboxService;
import com.siberalt.singularity.entity.order.OrderRepository;
import com.siberalt.singularity.event.EventManagerInterface;
import com.siberalt.singularity.entity.instrument.ReadInstrumentRepository;
import com.siberalt.singularity.entity.candle.ReadCandleRepository;
import com.siberalt.singularity.strategy.context.AbstractContext;
import com.siberalt.singularity.strategy.context.ContextAwareInterface;

class MockBroker implements StopOrderServiceAwareBrokerInterface, ContextAwareInterface, SandboxServiceAwareBroker {
    protected AbstractContext<?> context;
    protected MockMarketDataService marketDataService;
    protected MockOrderService orderService;
    protected MockOperationsService operationsService;
    protected MockInstrumentService instrumentService;
    protected MockUserService userService;
    protected MockSandboxService sandboxService;

    public MockBroker(
        ReadCandleRepository candleRepository,
        ReadInstrumentRepository instrumentStorage,
        OrderRepository orderRepository
    ) {
        orderService = new MockOrderService(this, orderRepository);
        marketDataService = new MockMarketDataService(this, candleRepository);
        operationsService = new MockOperationsService(this);
        instrumentService = new MockInstrumentService(this, instrumentStorage);
        userService = new MockUserService(this);
        sandboxService = new MockSandboxService(this);
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

    @Override
    public SandboxService getSandboxService() {
        return sandboxService;
    }
}
