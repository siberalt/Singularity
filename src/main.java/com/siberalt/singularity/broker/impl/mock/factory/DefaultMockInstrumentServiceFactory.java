package com.siberalt.singularity.broker.impl.mock.factory;

import com.siberalt.singularity.broker.impl.mock.MockInstrumentService;

public class DefaultMockInstrumentServiceFactory implements MockServiceFactory<MockInstrumentService> {
    @Override
    public MockInstrumentService create(MockServiceContext context) {
        return new MockInstrumentService(context.brokerId(), context.instrumentRepository());
    }
}
