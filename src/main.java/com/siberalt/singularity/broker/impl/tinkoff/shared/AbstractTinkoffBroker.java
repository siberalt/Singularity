package com.siberalt.singularity.broker.impl.tinkoff.shared;

import com.siberalt.singularity.broker.contract.execution.EventSubscriptionBroker;
import com.siberalt.singularity.broker.contract.service.instrument.InstrumentService;
import com.siberalt.singularity.broker.contract.service.market.MarketDataService;
import com.siberalt.singularity.broker.contract.service.operation.OperationsService;
import com.siberalt.singularity.broker.contract.service.order.OrderService;
import com.siberalt.singularity.broker.contract.service.user.UserService;
import com.siberalt.singularity.event.subscription.SubscriptionManager;
import ru.ttech.piapi.core.connector.ServiceStubFactory;

import java.io.Closeable;

/**
 * Holds the services a Tinkoff broker exposes. All the gRPC/stub wiring that builds them lives
 * in {@link TinkoffServicesFactory} (and the per-service factories in the {@code factory}
 * subpackage) - this class (and its subclasses) just stores what it's given and hands it back
 * out through the {@link EventSubscriptionBroker} getters.
 */
public abstract class AbstractTinkoffBroker implements EventSubscriptionBroker, Closeable {
    protected final ServiceStubFactory serviceStubFactory;
    protected final OrderService orderService;
    protected final MarketDataService marketDataService;
    protected final OperationsService operationsService;
    protected final UserService userService;
    protected final InstrumentService instrumentService;
    protected final SubscriptionManager subscriptionManager;

    protected AbstractTinkoffBroker(TinkoffServices services) {
        this.serviceStubFactory = services.serviceStubFactory();
        this.orderService = services.orderService();
        this.marketDataService = services.marketDataService();
        this.operationsService = services.operationsService();
        this.userService = services.userService();
        this.instrumentService = services.instrumentService();
        this.subscriptionManager = services.subscriptionManager();
    }

    @Override
    public void close() {
        serviceStubFactory.getChannel().shutdown();
    }

    @Override
    public MarketDataService getMarketDataService() {
        return marketDataService;
    }

    @Override
    public OperationsService getOperationsService() {
        return operationsService;
    }

    @Override
    public OrderService getOrderService() {
        return orderService;
    }

    @Override
    public UserService getUserService() {
        return userService;
    }

    @Override
    public InstrumentService getInstrumentService() {
        return instrumentService;
    }

    @Override
    public SubscriptionManager getSubscriptionManager() {
        return subscriptionManager;
    }
}
