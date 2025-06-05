package com.siberalt.singularity.broker.contract.execution;

import com.siberalt.singularity.broker.contract.service.market.MarketDataService;
import com.siberalt.singularity.broker.contract.service.operation.OperationsService;
import com.siberalt.singularity.broker.contract.service.user.UserService;
import com.siberalt.singularity.broker.contract.service.instrument.InstrumentService;
import com.siberalt.singularity.broker.contract.service.order.OrderService;

public interface Broker {
    InstrumentService getInstrumentService();
    MarketDataService getMarketDataService();
    OperationsService getOperationsService();
    OrderService getOrderService();
    UserService getUserService();
}