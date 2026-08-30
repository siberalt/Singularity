package com.siberalt.singularity.broker.impl.tinkoff.shared;

import com.siberalt.singularity.broker.contract.service.instrument.InstrumentService;
import com.siberalt.singularity.broker.contract.service.market.MarketDataService;
import com.siberalt.singularity.broker.contract.service.operation.OperationsService;
import com.siberalt.singularity.broker.contract.service.order.OrderService;
import com.siberalt.singularity.broker.contract.service.user.UserService;
import com.siberalt.singularity.event.subscription.SubscriptionManager;
import ru.ttech.piapi.core.connector.ServiceStubFactory;

/**
 * The set of services every Tinkoff broker flavor (sandbox, live execution) is built from.
 * Produced by {@link TinkoffServicesFactory} from one factory per service (see the
 * {@code factory} subpackage); brokers ({@link AbstractTinkoffBroker} and its subclasses) just
 * hold onto these plus whatever extra service they add - they don't know how to build any of it
 * themselves.
 */
public record TinkoffServices(
    ServiceStubFactory serviceStubFactory,
    OrderService orderService,
    MarketDataService marketDataService,
    OperationsService operationsService,
    UserService userService,
    InstrumentService instrumentService,
    SubscriptionManager subscriptionManager
) {
}
