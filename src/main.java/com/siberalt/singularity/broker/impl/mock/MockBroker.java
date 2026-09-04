package com.siberalt.singularity.broker.impl.mock;

import com.siberalt.singularity.broker.contract.execution.SandboxServiceAwareBroker;
import com.siberalt.singularity.broker.contract.execution.StopOrderServiceAwareBroker;
import com.siberalt.singularity.broker.contract.service.order.stop.StopOrderServiceInterface;
import com.siberalt.singularity.broker.contract.service.sandbox.SandboxService;
import com.siberalt.singularity.broker.impl.mock.factory.MockServiceContext;
import com.siberalt.singularity.broker.impl.mock.factory.MockServices;
import com.siberalt.singularity.broker.impl.mock.factory.MockServicesFactory;
import com.siberalt.singularity.entity.candle.ReadCandleRepository;
import com.siberalt.singularity.entity.instrument.ReadInstrumentRepository;
import com.siberalt.singularity.entity.operation.OperationRepository;
import com.siberalt.singularity.entity.order.OrderRepository;
import com.siberalt.singularity.strategy.context.Clock;

public class MockBroker implements
    StopOrderServiceAwareBroker,
    SandboxServiceAwareBroker
{
    public static final double DEFAULT_COMMISSION_RATIO = 0.003;
    public static final String DEFAULT_ID = "mock-broker";

    protected Clock clock;
    protected MockMarketDataService marketDataService;
    protected MockOrderService orderService;
    protected MockOperationsService operationsService;
    protected MockInstrumentService instrumentService;
    protected MockUserService userService;
    protected MockSandboxService sandboxService;
    protected String id;

    public MockBroker(
        ReadCandleRepository candleRepository,
        ReadInstrumentRepository instrumentStorage,
        OrderRepository orderRepository,
        OperationRepository operationRepository,
        Clock clock,
        double commissionRatio,
        String id,
        MockServicesFactory servicesFactory
    ) {
        this.clock = clock;
        this.id = id;

        MockServices services = servicesFactory.create(
            new MockServiceContext(clock, id, candleRepository, instrumentStorage, orderRepository, operationRepository, commissionRatio)
        );

        instrumentService = services.instrumentService();
        userService = services.userService();
        marketDataService = services.marketDataService();
        operationsService = services.operationsService();
        sandboxService = services.sandboxService();
        orderService = services.orderService();
    }

    public MockBroker(
        ReadCandleRepository candleRepository,
        ReadInstrumentRepository instrumentStorage,
        OrderRepository orderRepository,
        OperationRepository operationRepository,
        Clock clock,
        double commissionRatio,
        String id
    ) {
        this(candleRepository, instrumentStorage, orderRepository, operationRepository, clock, commissionRatio, id, new MockServicesFactory());
    }

    public MockBroker(
        ReadCandleRepository candleRepository,
        ReadInstrumentRepository instrumentStorage,
        OrderRepository orderRepository,
        OperationRepository operationRepository,
        Clock clock
    ) {
        this(candleRepository, instrumentStorage, orderRepository, operationRepository, clock, DEFAULT_COMMISSION_RATIO, DEFAULT_ID);
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
    public StopOrderServiceInterface getStopOrderService() {
        return null;
    }

    @Override
    public SandboxService getSandboxService() {
        return sandboxService;
    }

    public String getId() {
        return id;
    }
}
