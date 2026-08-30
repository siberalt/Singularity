package com.siberalt.singularity.broker.impl.tinkoff.shared.factory;

import ru.ttech.piapi.core.connector.ServiceStubFactory;

import java.util.function.UnaryOperator;

/**
 * Applies a {@link UnaryOperator} to whatever another {@link TinkoffServiceFactory} builds -
 * e.g. {@code new DecoratingServiceFactory<>(new TinkoffOrderServiceFactory(), LoggingOrderService::new)}
 * to have every order call logged. Works for any service, not just OrderService.
 */
public class DecoratingServiceFactory<T> implements TinkoffServiceFactory<T> {
    private final TinkoffServiceFactory<T> delegate;
    private final UnaryOperator<T> decorator;

    public DecoratingServiceFactory(TinkoffServiceFactory<T> delegate, UnaryOperator<T> decorator) {
        this.delegate = delegate;
        this.decorator = decorator;
    }

    @Override
    public T create(ServiceStubFactory serviceStubFactory) {
        return decorator.apply(delegate.create(serviceStubFactory));
    }
}
