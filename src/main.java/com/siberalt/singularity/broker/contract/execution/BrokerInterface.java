package com.siberalt.singularity.broker.contract.execution;

import com.siberalt.singularity.broker.contract.service.market.MarketDataServiceInterface;
import com.siberalt.singularity.broker.contract.service.operation.OperationsServiceInterface;
import com.siberalt.singularity.broker.contract.service.user.UserServiceInterface;
import com.siberalt.singularity.strategy.event.EventManagerInterface;
import com.siberalt.singularity.broker.contract.service.instrument.InstrumentServiceInterface;
import com.siberalt.singularity.broker.contract.service.order.OrderServiceInterface;

public interface BrokerInterface {
    InstrumentServiceInterface getInstrumentService();
    MarketDataServiceInterface getMarketDataService();
    OperationsServiceInterface getOperationsService();
    OrderServiceInterface getOrderService();
    UserServiceInterface getUserService();
    EventManagerInterface getEventManager();
}