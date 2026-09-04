package com.siberalt.singularity.broker.impl.mock.factory;

import com.siberalt.singularity.broker.impl.mock.MockInstrumentService;
import com.siberalt.singularity.broker.impl.mock.MockOperationsService;
import com.siberalt.singularity.broker.impl.mock.MockUserService;

public class DefaultOperationsServiceFactory implements OperationsServiceFactory {
    @Override
    public MockOperationsService create(
        MockServiceContext context,
        MockInstrumentService instrumentService,
        MockUserService userService
    ) {
        return new MockOperationsService(
            context.clock(),
            context.brokerId(),
            instrumentService,
            userService,
            context.operationRepository()
        );
    }
}
