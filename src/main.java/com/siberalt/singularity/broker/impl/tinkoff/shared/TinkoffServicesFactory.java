package com.siberalt.singularity.broker.impl.tinkoff.shared;

import com.siberalt.singularity.broker.contract.service.instrument.InstrumentService;
import com.siberalt.singularity.broker.contract.service.market.MarketDataService;
import com.siberalt.singularity.broker.contract.service.operation.OperationsService;
import com.siberalt.singularity.broker.contract.service.order.OrderService;
import com.siberalt.singularity.broker.contract.service.user.UserService;
import com.siberalt.singularity.broker.impl.tinkoff.shared.factory.TinkoffInstrumentServiceFactory;
import com.siberalt.singularity.broker.impl.tinkoff.shared.factory.TinkoffMarketDataServiceFactory;
import com.siberalt.singularity.broker.impl.tinkoff.shared.factory.TinkoffOperationsServiceFactory;
import com.siberalt.singularity.broker.impl.tinkoff.shared.factory.TinkoffOrderServiceFactory;
import com.siberalt.singularity.broker.impl.tinkoff.shared.factory.TinkoffServiceFactory;
import com.siberalt.singularity.broker.impl.tinkoff.shared.factory.TinkoffSubscriptionManagerFactory;
import com.siberalt.singularity.broker.impl.tinkoff.shared.factory.TinkoffUserServiceFactory;
import com.siberalt.singularity.event.subscription.SubscriptionManager;
import ru.ttech.piapi.core.connector.ConnectorConfiguration;
import ru.ttech.piapi.core.connector.ServiceStubFactory;

/**
 * Composition root for a Tinkoff broker's services: builds the shared {@link ServiceStubFactory}
 * from a {@link ConnectorConfiguration}, then hands it to one {@link TinkoffServiceFactory} per
 * service. Each factory defaults to the real Tinkoff implementation but can be swapped for a
 * decorated/alternate one - e.g.
 * {@code .orderServiceFactory(new DecoratingServiceFactory<>(new TinkoffOrderServiceFactory(), LoggingOrderService::new))}
 * to have every order call logged, without any broker class needing to know about it.
 */
public class TinkoffServicesFactory {
    private TinkoffServiceFactory<OrderService> orderServiceFactory = new TinkoffOrderServiceFactory();
    private TinkoffServiceFactory<MarketDataService> marketDataServiceFactory = new TinkoffMarketDataServiceFactory();
    private TinkoffServiceFactory<OperationsService> operationsServiceFactory = new TinkoffOperationsServiceFactory();
    private TinkoffServiceFactory<UserService> userServiceFactory = new TinkoffUserServiceFactory();
    private TinkoffServiceFactory<InstrumentService> instrumentServiceFactory = new TinkoffInstrumentServiceFactory();
    private TinkoffServiceFactory<SubscriptionManager> subscriptionManagerFactory = new TinkoffSubscriptionManagerFactory();

    public TinkoffServicesFactory orderServiceFactory(TinkoffServiceFactory<OrderService> factory) {
        this.orderServiceFactory = factory;
        return this;
    }

    public TinkoffServicesFactory marketDataServiceFactory(TinkoffServiceFactory<MarketDataService> factory) {
        this.marketDataServiceFactory = factory;
        return this;
    }

    public TinkoffServicesFactory operationsServiceFactory(TinkoffServiceFactory<OperationsService> factory) {
        this.operationsServiceFactory = factory;
        return this;
    }

    public TinkoffServicesFactory userServiceFactory(TinkoffServiceFactory<UserService> factory) {
        this.userServiceFactory = factory;
        return this;
    }

    public TinkoffServicesFactory instrumentServiceFactory(TinkoffServiceFactory<InstrumentService> factory) {
        this.instrumentServiceFactory = factory;
        return this;
    }

    public TinkoffServicesFactory subscriptionManagerFactory(TinkoffServiceFactory<SubscriptionManager> factory) {
        this.subscriptionManagerFactory = factory;
        return this;
    }

    public TinkoffServices create(ConnectorConfiguration configuration) {
        ServiceStubFactory serviceStubFactory = ServiceStubFactory.create(configuration);

        return new TinkoffServices(
            serviceStubFactory,
            orderServiceFactory.create(serviceStubFactory),
            marketDataServiceFactory.create(serviceStubFactory),
            operationsServiceFactory.create(serviceStubFactory),
            userServiceFactory.create(serviceStubFactory),
            instrumentServiceFactory.create(serviceStubFactory),
            subscriptionManagerFactory.create(serviceStubFactory)
        );
    }
}
