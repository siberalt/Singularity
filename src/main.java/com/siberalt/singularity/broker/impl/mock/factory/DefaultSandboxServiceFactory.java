package com.siberalt.singularity.broker.impl.mock.factory;

import com.siberalt.singularity.broker.impl.mock.MockOperationsService;
import com.siberalt.singularity.broker.impl.mock.MockSandboxService;
import com.siberalt.singularity.broker.impl.mock.MockUserService;

public class DefaultSandboxServiceFactory implements SandboxServiceFactory {
    @Override
    public MockSandboxService create(MockUserService userService, MockOperationsService operationsService) {
        return new MockSandboxService(userService, operationsService);
    }
}
