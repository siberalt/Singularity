package investtech.broker.impl.tinkoff;

import investtech.broker.contract.run.BrokerInterface;
import investtech.broker.contract.service.market.MarketDataServiceInterface;
import investtech.broker.contract.service.operation.OperationsServiceInterface;
import investtech.broker.contract.service.order.OrderServiceInterface;
import investtech.broker.contract.service.user.UserServiceInterface;
import investtech.strategy.event.EventManagerInterface;
import ru.tinkoff.piapi.contract.v1.OperationsServiceGrpc;
import ru.tinkoff.piapi.core.InvestApi;

public abstract class AbstractTinkoffBroker implements BrokerInterface {
    protected InvestApi api;

    protected OrderService orderService;

    protected MarketDataService marketDataService;

    protected OperationsService operationsService;

    protected UserService userService;

    protected InstrumentService instrumentService;

    public AbstractTinkoffBroker(String token) {
        init(token);
    }

    protected void init(String token) {
        api = createApi(token);
        var channel = api.getChannel();
        orderService = new OrderService(api.getOrdersService());
        marketDataService = new MarketDataService(api.getMarketDataService());
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
