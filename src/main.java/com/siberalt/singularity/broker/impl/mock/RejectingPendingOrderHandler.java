package com.siberalt.singularity.broker.impl.mock;

import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.contract.service.exception.ErrorCode;
import com.siberalt.singularity.broker.contract.service.exception.ExceptionBuilder;
import com.siberalt.singularity.entity.order.Order;

/**
 * Refuses orders that cannot fill right away. This is the plain mock broker's behaviour: it has no
 * notion of time passing, so an order that has to wait for the market can never be filled.
 * Simulating the wait is {@link SimulatedPendingOrderHandler}'s job.
 */
public class RejectingPendingOrderHandler implements PendingOrderHandler {
    @Override
    public void onNotFillable(Order order) throws AbstractException {
        throw ExceptionBuilder
            .newBuilder(ErrorCode.UNIMPLEMENTED)
            .withMessage("Limit orders are not implemented yet")
            .build();
    }

    @Override
    public void onCancelled(Order order) {
        // Nothing was ever scheduled, so there is nothing to unschedule.
    }
}
