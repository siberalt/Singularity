package com.siberalt.singularity.broker.impl.tinkoff.shared;

import com.siberalt.singularity.broker.contract.service.user.UserServiceInterface;
import com.siberalt.singularity.broker.contract.execution.TechAnalysisServiceAwareBrokerInterface;
import com.siberalt.singularity.broker.contract.service.market.MarketDataServiceInterface;
import com.siberalt.singularity.broker.contract.service.market.TechAnalysisServiceInterface;
import com.siberalt.singularity.broker.contract.service.operation.OperationsServiceInterface;
import com.siberalt.singularity.broker.contract.service.order.OrderServiceInterface;
import com.siberalt.singularity.event.EventManagerInterface;
import ru.tinkoff.piapi.contract.v1.OperationsServiceGrpc;
import ru.tinkoff.piapi.core.InvestApi;

public abstract class AbstractTinkoffBroker implements TechAnalysisServiceAwareBrokerInterface {
    protected InvestApi api;
    protected OrderService orderService;
    protected MarketDataService marketDataService;
    protected OperationsService operationsService;
    protected UserService userService;
    protected InstrumentService instrumentService;
    protected TechAnalysisService techAnalysisService;

    public AbstractTinkoffBroker(String token) {
        init(token);
    }

    protected void init(String token) {
        api = createApi(token);
        var channel = api.getChannel();
        orderService = new OrderService(api.getOrdersService());
        marketDataService = new MarketDataService(api.getMarketDataService());
        techAnalysisService = new TechAnalysisService(api.getMarketDataService());
        operationsService = new OperationsService(OperationsServiceGrpc.newBlockingStub(channel));
        userService = new UserService(api.getUserService());
        instrumentService = new InstrumentService(api.getInstrumentsService());
    }

    protected InvestApi createApi(String token) {
        return InvestApi.create(token);
    }

    @Override
    public MarketDataServiceInterface getMarketDataService() {
        return marketDataService;
    }

    @Override
    public TechAnalysisServiceInterface getTechAnalysisService() {
        return techAnalysisService;
    }

    @Override
    public OperationsServiceInterface getOperationsService() {
        return operationsService;
    }

    @Override
    public OrderServiceInterface getOrderService() {
        return orderService;
    }

    @Override
    public UserServiceInterface getUserService() {
        return userService;
    }

    @Override
    public EventManagerInterface getEventManager() {
        return null;
    }

    @Override
    public InstrumentService getInstrumentService() {
        return instrumentService;
    }
}
