package com.siberalt.singularity.broker.contract.execution;

import com.siberalt.singularity.broker.contract.service.order.stop.StopOrderServiceInterface;

public interface StopOrderServiceAwareBrokerInterface extends BrokerInterface {
    StopOrderServiceInterface getStopOrderService();
}
