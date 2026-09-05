package com.siberalt.singularity.broker.impl.mock.factory;

import com.siberalt.singularity.broker.impl.mock.MockInstrumentService;
import com.siberalt.singularity.broker.impl.mock.SimulationMarketData;
import com.siberalt.singularity.broker.impl.mock.MockOperationsService;
import com.siberalt.singularity.broker.impl.mock.MockOrderService;
import com.siberalt.singularity.broker.impl.mock.MockUserService;

/**
 * Builds {@link MockOrderService}. It depends on every other mock service, so - like
 * {@link OperationsServiceFactory} and {@link SandboxServiceFactory} - it takes them as explicit
 * parameters rather than through the context-only {@link MockServiceFactory} shape.
 */
public interface OrderServiceFactory {
    MockOrderService create(
        MockServiceContext context,
        MockOperationsService operationsService,
        MockInstrumentService instrumentService,
        SimulationMarketData marketDataService,
        MockUserService userService
    );
}
