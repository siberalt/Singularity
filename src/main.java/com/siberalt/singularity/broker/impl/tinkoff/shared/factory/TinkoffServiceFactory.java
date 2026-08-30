package com.siberalt.singularity.broker.impl.tinkoff.shared.factory;

import ru.ttech.piapi.core.connector.ServiceStubFactory;

/**
 * Builds one Tinkoff-backed service from the shared {@link ServiceStubFactory}. One interface
 * for every service (order, market data, operations, ...) - what varies is {@code T}, not the
 * shape of "how do I build one".
 */
public interface TinkoffServiceFactory<T> {
    T create(ServiceStubFactory serviceStubFactory);
}
