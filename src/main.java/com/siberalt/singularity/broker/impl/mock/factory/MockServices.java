package com.siberalt.singularity.broker.impl.mock.factory;

import com.siberalt.singularity.broker.impl.mock.MockInstrumentService;
import com.siberalt.singularity.broker.impl.mock.MockMarketDataService;
import com.siberalt.singularity.broker.impl.mock.MockOperationsService;
import com.siberalt.singularity.broker.impl.mock.MockOrderService;
import com.siberalt.singularity.broker.impl.mock.MockSandboxService;
import com.siberalt.singularity.broker.impl.mock.MockUserService;

/**
 * The set of services a mock broker is built from. Produced by {@link MockServicesFactory} from
 * one factory per service; {@code MockBroker} just holds onto these and hands them back out
 * through its getters - it doesn't know how to build any of it itself. Mirrors Tinkoff's
 * {@code TinkoffServices}.
 */
public record MockServices(
    MockInstrumentService instrumentService,
    MockUserService userService,
    MockMarketDataService marketDataService,
    MockOperationsService operationsService,
    MockSandboxService sandboxService,
    MockOrderService orderService
) {
}
