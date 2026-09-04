package com.siberalt.singularity.broker.impl.mock.factory;

import com.siberalt.singularity.broker.contract.service.order.CommissionTransactionSpecProvider;
import com.siberalt.singularity.broker.contract.service.order.OrderTransactionSpecProvider;
import com.siberalt.singularity.broker.impl.mock.MockInstrumentService;
import com.siberalt.singularity.broker.impl.mock.MockMarketDataService;
import com.siberalt.singularity.broker.impl.mock.MockOperationsService;
import com.siberalt.singularity.broker.impl.mock.MockOrderService;
import com.siberalt.singularity.broker.impl.mock.MockUserService;

public class DefaultOrderServiceFactory implements OrderServiceFactory {
    @Override
    public MockOrderService create(
        MockServiceContext context,
        MockOperationsService operationsService,
        MockInstrumentService instrumentService,
        MockMarketDataService marketDataService,
        MockUserService userService
    ) {
        return new MockOrderService(
            context.clock(),
            operationsService,
            instrumentService,
            marketDataService,
            userService,
            context.orderRepository(),
            context.operationRepository(),
            new CommissionTransactionSpecProvider(context.commissionRatio()),
            new OrderTransactionSpecProvider()
        );
    }
}
