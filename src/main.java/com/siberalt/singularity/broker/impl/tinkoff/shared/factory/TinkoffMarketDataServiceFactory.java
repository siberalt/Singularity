package com.siberalt.singularity.broker.impl.tinkoff.shared.factory;

import com.siberalt.singularity.broker.contract.service.market.MarketDataService;
import ru.tinkoff.piapi.contract.v1.MarketDataServiceGrpc;
import ru.ttech.piapi.core.connector.ServiceStubFactory;

public class TinkoffMarketDataServiceFactory implements TinkoffServiceFactory<MarketDataService> {
    @Override
    public MarketDataService create(ServiceStubFactory serviceStubFactory) {
        var marketDataServiceStub = serviceStubFactory.newSyncService(MarketDataServiceGrpc::newBlockingStub).getStub();

        return new com.siberalt.singularity.broker.impl.tinkoff.shared.MarketDataService(marketDataServiceStub);
    }
}
