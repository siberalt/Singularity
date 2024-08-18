package investtech.broker.contract.run;

import investtech.broker.contract.service.order.stop.StopOrderServiceInterface;

public interface StopOrderServiceAwareBrokerInterface extends BrokerInterface{
    StopOrderServiceInterface getStopOrderService();
}
