package com.siberalt.singularity.broker.impl.mock.factory;

import com.siberalt.singularity.broker.contract.service.order.CommissionTransactionSpecProvider;
import com.siberalt.singularity.broker.contract.service.order.OrderTransactionSpecProvider;
import com.siberalt.singularity.broker.contract.service.order.TransactionSpecProvider;
import com.siberalt.singularity.broker.impl.mock.DefaultOrderExecutor;
import com.siberalt.singularity.broker.impl.mock.MockInstrumentService;
import com.siberalt.singularity.broker.impl.mock.MockMarketDataService;
import com.siberalt.singularity.broker.impl.mock.MockOperationsService;
import com.siberalt.singularity.broker.impl.mock.MockOrderService;
import com.siberalt.singularity.broker.impl.mock.MockUserService;
import com.siberalt.singularity.broker.impl.mock.OrderExecutor;
import com.siberalt.singularity.broker.impl.mock.OrderPriceModel;
import com.siberalt.singularity.broker.impl.mock.OrderRegistry;
import com.siberalt.singularity.broker.impl.mock.PendingOrderHandler;
import com.siberalt.singularity.broker.impl.mock.RejectingPendingOrderHandler;

/**
 * Builds an order service that can only fill orders the market is ready for right now - anything
 * that would have to wait is rejected by {@link RejectingPendingOrderHandler}. Simulating the wait
 * needs a clock that advances, which is {@link DefaultEventOrderServiceFactory}'s job.
 * <p>
 * Subclasses hook into the pieces through the {@code create*} methods rather than by rebuilding
 * the whole service.
 */
public class DefaultOrderServiceFactory implements OrderServiceFactory {
    private final TransactionSpecProvider commissionTransactionSpecProvider;
    private final TransactionSpecProvider orderTransactionSpecProvider;

    public DefaultOrderServiceFactory() {
        this(null, null);
    }

    public DefaultOrderServiceFactory(
        TransactionSpecProvider commissionTransactionSpecProvider,
        TransactionSpecProvider orderTransactionSpecProvider
    ) {
        this.commissionTransactionSpecProvider = commissionTransactionSpecProvider;
        this.orderTransactionSpecProvider = orderTransactionSpecProvider;
    }

    @Override
    public MockOrderService create(
        MockServiceContext context,
        MockOperationsService operationsService,
        MockInstrumentService instrumentService,
        MockMarketDataService marketDataService,
        MockUserService userService
    ) {
        OrderPriceModel priceModel = createPriceModel(context);
        OrderRegistry orderRegistry = createOrderRegistry(context);
        OrderExecutor orderExecutor = createOrderExecutor(context, operationsService, orderRegistry);

        return new MockOrderService(
            context.clock(),
            operationsService,
            instrumentService,
            marketDataService,
            userService,
            context.orderRepository(),
            orderRegistry,
            priceModel,
            orderExecutor,
            createPendingOrderHandler(
                context,
                orderExecutor,
                orderRegistry,
                priceModel,
                marketDataService,
                operationsService
            )
        );
    }

    protected OrderRegistry createOrderRegistry(MockServiceContext context) {
        return new OrderRegistry(context.orderRepository(), context.operationRepository());
    }

    protected OrderPriceModel createPriceModel(MockServiceContext context) {
        return new OrderPriceModel();
    }

    protected OrderExecutor createOrderExecutor(
        MockServiceContext context,
        MockOperationsService operationsService,
        OrderRegistry orderRegistry
    ) {
        return new DefaultOrderExecutor(
            context.clock(),
            operationsService,
            orderRegistry,
            context.operationRepository(),
            commissionTransactionSpecProvider != null
                ? commissionTransactionSpecProvider
                : new CommissionTransactionSpecProvider(context.commissionRatio()),
            orderTransactionSpecProvider != null
                ? orderTransactionSpecProvider
                : new OrderTransactionSpecProvider()
        );
    }

    protected PendingOrderHandler createPendingOrderHandler(
        MockServiceContext context,
        OrderExecutor orderExecutor,
        OrderRegistry orderRegistry,
        OrderPriceModel priceModel,
        MockMarketDataService marketDataService,
        MockOperationsService operationsService
    ) {
        return new RejectingPendingOrderHandler();
    }
}
