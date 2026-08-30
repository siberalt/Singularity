package com.siberalt.singularity.broker.impl.tinkoff.shared.factory;

import com.siberalt.singularity.broker.contract.service.order.OrderService;
import ru.tinkoff.piapi.contract.v1.MarketDataServiceGrpc;
import ru.tinkoff.piapi.contract.v1.OrdersServiceGrpc;
import ru.ttech.piapi.core.connector.ServiceStubFactory;

public class TinkoffOrderServiceFactory implements TinkoffServiceFactory<OrderService> {
    @Override
    public OrderService create(ServiceStubFactory serviceStubFactory) {
        var marketDataServiceStub = serviceStubFactory.newSyncService(MarketDataServiceGrpc::newBlockingStub).getStub();

        return new com.siberalt.singularity.broker.impl.tinkoff.shared.OrderService(
            serviceStubFactory.newSyncService(OrdersServiceGrpc::newBlockingStub).getStub(),
            marketDataServiceStub
        );
    }
}
