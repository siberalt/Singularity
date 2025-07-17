package com.siberalt.singularity.broker.contract.execution;

import com.siberalt.singularity.broker.contract.service.order.stop.StopOrderServiceInterface;

public interface StopOrderServiceAwareBroker extends Broker {
    StopOrderServiceInterface getStopOrderService();
}
