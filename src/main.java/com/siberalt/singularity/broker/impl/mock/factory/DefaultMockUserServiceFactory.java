package com.siberalt.singularity.broker.impl.mock.factory;

import com.siberalt.singularity.broker.impl.mock.MockUserService;

public class DefaultMockUserServiceFactory implements MockServiceFactory<MockUserService> {
    @Override
    public MockUserService create(MockServiceContext context) {
        return new MockUserService(context.clock());
    }
}
