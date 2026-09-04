package com.siberalt.singularity.broker.impl.mock.factory;

import com.siberalt.singularity.broker.contract.service.order.CommissionTransactionSpecProvider;
import com.siberalt.singularity.broker.contract.service.order.OrderTransactionSpecProvider;
import com.siberalt.singularity.broker.contract.service.order.TransactionSpecProvider;
import com.siberalt.singularity.broker.impl.mock.EventSimulatedOrderService;
import com.siberalt.singularity.broker.impl.mock.MockInstrumentService;
import com.siberalt.singularity.broker.impl.mock.MockMarketDataService;
import com.siberalt.singularity.broker.impl.mock.MockOperationsService;
import com.siberalt.singularity.broker.impl.mock.MockUserService;

/**
 * The {@link OrderServiceFactory} an {@code EventMockBroker} plugs into
 * {@link MockServicesFactory} instead of {@link DefaultOrderServiceFactory} - builds an
 * {@link EventSimulatedOrderService} from the exact same inputs, so the event-driven broker still
 * goes through the shared composition root rather than wiring its order service by hand.
 * <p>
 * The commission and order transaction spec providers can be overridden through the constructor;
 * left unset (the no-arg constructor), each defaults the same way {@link DefaultOrderServiceFactory}
 * does - commission from {@link MockServiceContext#commissionRatio()}, order unconditionally.
 */
public class DefaultEventOrderServiceFactory implements OrderServiceFactory {
    private final TransactionSpecProvider commissionTransactionSpecProvider;
    private final TransactionSpecProvider orderTransactionSpecProvider;

    public DefaultEventOrderServiceFactory() {
        this(null, null);
    }

    public DefaultEventOrderServiceFactory(
        TransactionSpecProvider commissionTransactionSpecProvider,
        TransactionSpecProvider orderTransactionSpecProvider
    ) {
        this.commissionTransactionSpecProvider = commissionTransactionSpecProvider;
        this.orderTransactionSpecProvider = orderTransactionSpecProvider;
    }

    @Override
    public EventSimulatedOrderService create(
        MockServiceContext context,
        MockOperationsService operationsService,
        MockInstrumentService instrumentService,
        MockMarketDataService marketDataService,
        MockUserService userService
    ) {
        return new EventSimulatedOrderService(
            context.clock(),
            operationsService,
            instrumentService,
            marketDataService,
            userService,
            context.orderRepository(),
            context.operationRepository(),
            commissionTransactionSpecProvider != null
                ? commissionTransactionSpecProvider
                : new CommissionTransactionSpecProvider(context.commissionRatio()),
            orderTransactionSpecProvider != null
                ? orderTransactionSpecProvider
                : new OrderTransactionSpecProvider()
        );
    }
}
