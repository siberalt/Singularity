package com.siberalt.singularity.broker.impl.mock.factory;

import com.siberalt.singularity.broker.contract.service.order.TransactionSpecProvider;
import com.siberalt.singularity.broker.impl.mock.LoggingOrderExecutor;
import com.siberalt.singularity.broker.impl.mock.MockMarketDataService;
import com.siberalt.singularity.broker.impl.mock.MockOperationsService;
import com.siberalt.singularity.broker.impl.mock.OrderExecutor;
import com.siberalt.singularity.broker.impl.mock.OrderPriceModel;
import com.siberalt.singularity.broker.impl.mock.OrderRegistry;
import com.siberalt.singularity.broker.impl.mock.SimulatedPendingOrderHandler;

/**
 * The {@link OrderServiceFactory} an {@code EventMockBroker} plugs into {@link MockServicesFactory}
 * instead of {@link DefaultOrderServiceFactory}. The order service itself is identical - the only
 * difference is what happens to an order the market is not ready for, so this overrides just the
 * pending-order handler, plus wraps the executor in a {@link LoggingOrderExecutor}: a simulation
 * run is worth following in the log, and fills scheduled for later would otherwise happen silently.
 */
public class DefaultEventOrderServiceFactory extends DefaultOrderServiceFactory {
    public DefaultEventOrderServiceFactory() {
        super();
    }

    public DefaultEventOrderServiceFactory(
        TransactionSpecProvider commissionTransactionSpecProvider,
        TransactionSpecProvider orderTransactionSpecProvider
    ) {
        super(commissionTransactionSpecProvider, orderTransactionSpecProvider);
    }

    @Override
    protected OrderExecutor createOrderExecutor(
        MockServiceContext context,
        MockOperationsService operationsService,
        OrderRegistry orderRegistry
    ) {
        return new LoggingOrderExecutor(
            super.createOrderExecutor(context, operationsService, orderRegistry),
            context.clock()
        );
    }

    @Override
    protected SimulatedPendingOrderHandler createPendingOrderHandler(
        MockServiceContext context,
        OrderExecutor orderExecutor,
        OrderRegistry orderRegistry,
        OrderPriceModel priceModel,
        MockMarketDataService marketDataService,
        MockOperationsService operationsService
    ) {
        return new SimulatedPendingOrderHandler(
            context.clock(),
            orderExecutor,
            orderRegistry,
            priceModel,
            marketDataService,
            operationsService
        );
    }
}
