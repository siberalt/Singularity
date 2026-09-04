package com.siberalt.singularity.broker.impl.mock.factory;

import com.siberalt.singularity.broker.impl.mock.MockInstrumentService;
import com.siberalt.singularity.broker.impl.mock.MockMarketDataService;
import com.siberalt.singularity.broker.impl.mock.MockOperationsService;
import com.siberalt.singularity.broker.impl.mock.MockOrderService;
import com.siberalt.singularity.broker.impl.mock.MockSandboxService;
import com.siberalt.singularity.broker.impl.mock.MockUserService;

/**
 * Composition root for a mock broker's services: builds each one from the shared
 * {@link MockServiceContext}, in dependency order (instrument/user/market data first, since
 * operations/sandbox/order depend on them), then hands them to {@link MockServices}. Mirrors
 * Tinkoff's {@code TinkoffServicesFactory}, including the ability to swap in an alternate factory
 * for a given service - e.g. to decorate order execution with logging.
 */
public class MockServicesFactory {
    private MockServiceFactory<MockInstrumentService> instrumentServiceFactory = new DefaultMockInstrumentServiceFactory();
    private MockServiceFactory<MockUserService> userServiceFactory = new DefaultMockUserServiceFactory();
    private MockServiceFactory<MockMarketDataService> marketDataServiceFactory = new DefaultMockMarketDataServiceFactory();
    private OperationsServiceFactory operationsServiceFactory = new DefaultOperationsServiceFactory();
    private SandboxServiceFactory sandboxServiceFactory = new DefaultSandboxServiceFactory();
    private OrderServiceFactory orderServiceFactory = new DefaultOrderServiceFactory();

    public MockServicesFactory instrumentServiceFactory(MockServiceFactory<MockInstrumentService> factory) {
        this.instrumentServiceFactory = factory;
        return this;
    }

    public MockServicesFactory userServiceFactory(MockServiceFactory<MockUserService> factory) {
        this.userServiceFactory = factory;
        return this;
    }

    public MockServicesFactory marketDataServiceFactory(MockServiceFactory<MockMarketDataService> factory) {
        this.marketDataServiceFactory = factory;
        return this;
    }

    public MockServicesFactory operationsServiceFactory(OperationsServiceFactory factory) {
        this.operationsServiceFactory = factory;
        return this;
    }

    public MockServicesFactory sandboxServiceFactory(SandboxServiceFactory factory) {
        this.sandboxServiceFactory = factory;
        return this;
    }

    public MockServicesFactory orderServiceFactory(OrderServiceFactory factory) {
        this.orderServiceFactory = factory;
        return this;
    }

    public MockServices create(MockServiceContext context) {
        MockInstrumentService instrumentService = instrumentServiceFactory.create(context);
        MockUserService userService = userServiceFactory.create(context);
        MockMarketDataService marketDataService = marketDataServiceFactory.create(context);
        MockOperationsService operationsService = operationsServiceFactory.create(context, instrumentService, userService);
        MockSandboxService sandboxService = sandboxServiceFactory.create(userService, operationsService);
        MockOrderService orderService = orderServiceFactory.create(
            context,
            operationsService,
            instrumentService,
            marketDataService,
            userService
        );

        return new MockServices(instrumentService, userService, marketDataService, operationsService, sandboxService, orderService);
    }
}
