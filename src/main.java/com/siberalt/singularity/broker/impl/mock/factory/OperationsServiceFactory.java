package com.siberalt.singularity.broker.impl.mock.factory;

import com.siberalt.singularity.broker.impl.mock.MockInstrumentService;
import com.siberalt.singularity.broker.impl.mock.MockOperationsService;
import com.siberalt.singularity.broker.impl.mock.MockUserService;

/**
 * Builds {@link MockOperationsService}. Unlike {@link MockServiceFactory}, it also needs the
 * already-built instrument and user services, so it takes them as explicit parameters instead of
 * going through the context-only shape.
 */
public interface OperationsServiceFactory {
    MockOperationsService create(
        MockServiceContext context,
        MockInstrumentService instrumentService,
        MockUserService userService
    );
}
