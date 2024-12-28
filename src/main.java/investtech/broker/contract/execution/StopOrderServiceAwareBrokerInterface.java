package investtech.broker.contract.execution;

import investtech.broker.contract.service.order.stop.StopOrderServiceInterface;

public interface StopOrderServiceAwareBrokerInterface extends BrokerInterface{
    StopOrderServiceInterface getStopOrderService();
}
