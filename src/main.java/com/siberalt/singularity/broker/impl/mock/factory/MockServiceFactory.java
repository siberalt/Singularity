package com.siberalt.singularity.broker.impl.mock.factory;

/**
 * Builds one mock-broker service from the shared {@link MockServiceContext}. Mirrors Tinkoff's
 * {@code TinkoffServiceFactory<T>} - used for the services that need nothing but the shared
 * context. The services that also depend on a sibling service (operations, sandbox, order) take
 * that dependency as an explicit extra parameter instead of forcing it through this shape.
 */
public interface MockServiceFactory<T> {
    T create(MockServiceContext context);
}
