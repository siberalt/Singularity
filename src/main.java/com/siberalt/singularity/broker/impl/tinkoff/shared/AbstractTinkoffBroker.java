package com.siberalt.singularity.broker.impl.tinkoff.shared;

import com.siberalt.singularity.broker.contract.execution.EventSubscriptionBroker;
import com.siberalt.singularity.broker.contract.service.market.MarketDataService;
import com.siberalt.singularity.broker.contract.service.operation.OperationsService;
import com.siberalt.singularity.broker.contract.service.order.OrderService;
import com.siberalt.singularity.broker.contract.service.user.UserService;
import ru.tinkoff.piapi.contract.v1.OperationsServiceGrpc;
import ru.tinkoff.piapi.core.InvestApi;

import java.io.Closeable;

public abstract class AbstractTinkoffBroker implements EventSubscriptionBroker, Closeable {
    protected InvestApi api;
    protected com.siberalt.singularity.broker.impl.tinkoff.shared.OrderService orderService;
    protected com.siberalt.singularity.broker.impl.tinkoff.shared.MarketDataService marketDataService;
    protected com.siberalt.singularity.broker.impl.tinkoff.shared.OperationsService operationsService;
    protected com.siberalt.singularity.broker.impl.tinkoff.shared.UserService userService;
    protected InstrumentService instrumentService;
    protected SubscriptionManager subscriptionManager;

    public AbstractTinkoffBroker(String token) {
        init(token);
    }

    protected void init(String token) {
        api = createApi(token);
        var channel = api.getChannel();
        orderService = new com.siberalt.singularity.broker.impl.tinkoff.shared.OrderService(api.getOrdersService(), this);
        marketDataService = new com.siberalt.singularity.broker.impl.tinkoff.shared.MarketDataService(api.getMarketDataService());
        operationsService = new com.siberalt.singularity.broker.impl.tinkoff.shared.OperationsService(OperationsServiceGrpc.newBlockingStub(channel));
        userService = new com.siberalt.singularity.broker.impl.tinkoff.shared.UserService(api.getUserService());
        instrumentService = new InstrumentService(api.getInstrumentsService());
        subscriptionManager = new SubscriptionManager(api);
    }

    public void close() {
        if (null != api) {
            api.destroy(10);
            api = null;
        }
    }

    protected InvestApi createApi(String token) {
        return InvestApi.create(token);
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
