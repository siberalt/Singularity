package com.siberalt.singularity.broker.impl.mock.factory;

import com.siberalt.singularity.broker.impl.mock.MockMarketDataService;

public class DefaultMockMarketDataServiceFactory implements MockServiceFactory<MockMarketDataService> {
    @Override
    public MockMarketDataService create(MockServiceContext context) {
        return new MockMarketDataService(context.clock(), context.candleRepository());
    }
}
