package investtech.broker.contract.execution;

import investtech.broker.contract.service.instrument.InstrumentServiceInterface;
import investtech.broker.contract.service.market.MarketDataServiceInterface;
import investtech.broker.contract.service.operation.OperationsServiceInterface;
import investtech.broker.contract.service.order.OrderServiceInterface;
import investtech.broker.contract.service.user.UserServiceInterface;
import investtech.strategy.event.EventManagerInterface;

public interface BrokerInterface {
    InstrumentServiceInterface getInstrumentService();
    MarketDataServiceInterface getMarketDataService();
    OperationsServiceInterface getOperationsService();
    OrderServiceInterface getOrderService();
    UserServiceInterface getUserService();
    EventManagerInterface getEventManager();
}