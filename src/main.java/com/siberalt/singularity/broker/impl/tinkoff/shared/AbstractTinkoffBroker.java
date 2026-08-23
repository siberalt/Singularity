package com.siberalt.singularity.broker.impl.tinkoff.shared;

import com.siberalt.singularity.broker.contract.execution.EventSubscriptionBroker;
import com.siberalt.singularity.broker.contract.service.market.MarketDataService;
import com.siberalt.singularity.broker.contract.service.operation.OperationsService;
import com.siberalt.singularity.broker.contract.service.order.OrderService;
import com.siberalt.singularity.broker.contract.service.user.UserService;
import ru.tinkoff.piapi.contract.v1.*;
import ru.ttech.piapi.core.connector.ConnectorConfiguration;
import ru.ttech.piapi.core.connector.ServiceStubFactory;
import ru.ttech.piapi.core.connector.streaming.StreamManagerFactory;
import ru.ttech.piapi.core.connector.streaming.StreamServiceStubFactory;

import java.io.Closeable;
import java.util.concurrent.Executors;

public abstract class AbstractTinkoffBroker implements EventSubscriptionBroker, Closeable {
    protected ServiceStubFactory serviceStubFactory;
    protected com.siberalt.singularity.broker.impl.tinkoff.shared.OrderService orderService;
    protected com.siberalt.singularity.broker.impl.tinkoff.shared.MarketDataService marketDataService;
    protected com.siberalt.singularity.broker.impl.tinkoff.shared.OperationsService operationsService;
    protected com.siberalt.singularity.broker.impl.tinkoff.shared.UserService userService;
    protected InstrumentService instrumentService;
    protected SubscriptionManager subscriptionManager;

    public AbstractTinkoffBroker(ConnectorConfiguration configuration) {
        init(configuration);
    }

    protected void init(ConnectorConfiguration configuration) {
        serviceStubFactory = ServiceStubFactory.create(configuration);
        var marketDataServiceStub = serviceStubFactory.newSyncService(MarketDataServiceGrpc::newBlockingStub).getStub();

        orderService = new com.siberalt.singularity.broker.impl.tinkoff.shared.OrderService(
            serviceStubFactory.newSyncService(OrdersServiceGrpc::newBlockingStub).getStub(), marketDataServiceStub
        );
        marketDataService = new com.siberalt.singularity.broker.impl.tinkoff.shared.MarketDataService(marketDataServiceStub);
        operationsService = new com.siberalt.singularity.broker.impl.tinkoff.shared.OperationsService(
            serviceStubFactory.newSyncService(OperationsServiceGrpc::newBlockingStub).getStub()
        );
        userService = new com.siberalt.singularity.broker.impl.tinkoff.shared.UserService(
            serviceStubFactory.newSyncService(UsersServiceGrpc::newBlockingStub).getStub()
        );
        instrumentService = new InstrumentService(
            serviceStubFactory.newSyncService(InstrumentsServiceGrpc::newBlockingStub).getStub()
        );

        var streamFactory = StreamServiceStubFactory.create(serviceStubFactory);
        var streamManagerFactory = StreamManagerFactory.create(streamFactory);

        var executorService = Executors.newCachedThreadPool();
        var scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
        var marketDataStreamManager = streamManagerFactory.newMarketDataStreamManager(executorService, scheduledExecutorService);

        subscriptionManager = new SubscriptionManager(marketDataStreamManager);
    }

    @Override
    public void close() {
        if (serviceStubFactory != null) {
            // 4. Закрываем ресурсы через новый метод close() (destroy(int) устарел)
            serviceStubFactory.getChannel().shutdown();
            serviceStubFactory = null;
        }
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