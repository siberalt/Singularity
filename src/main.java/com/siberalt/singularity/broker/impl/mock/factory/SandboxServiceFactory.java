package com.siberalt.singularity.broker.impl.mock.factory;

import com.siberalt.singularity.broker.impl.mock.MockOperationsService;
import com.siberalt.singularity.broker.impl.mock.MockSandboxService;
import com.siberalt.singularity.broker.impl.mock.MockUserService;

/**
 * Builds {@link MockSandboxService} from the already-built user and operations services - it
 * needs no part of {@link MockServiceContext} itself.
 */
public interface SandboxServiceFactory {
    MockSandboxService create(MockUserService userService, MockOperationsService operationsService);
}
