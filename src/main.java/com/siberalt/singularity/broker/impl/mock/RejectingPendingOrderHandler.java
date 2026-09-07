package com.siberalt.singularity.broker.impl.mock;

import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.contract.service.exception.ErrorCode;
import com.siberalt.singularity.broker.contract.service.exception.ExceptionBuilder;
import com.siberalt.singularity.entity.order.Order;

import java.time.Instant;

/**
 * Refuses orders that cannot fill right away. This is the plain mock broker's behaviour: it has no
 * notion of time passing, so an order that has to wait for the market can never be filled.
 * Simulating the wait is {@link SimulatedPendingOrderHandler}'s job.
 */
public class RejectingPendingOrderHandler implements PendingOrderHandler {
    /**
     * There is no later here, whatever the order is waiting for - a price the market has not
     * reached, or the trip to the exchange a configured latency stands for. Refusing beats
     * silently ignoring the wait, which would hand back a fill at a price the order could not
     * have got.
     */
    @Override
    public void onPending(Order order, Instant tradableFrom) throws AbstractException {
        throw ExceptionBuilder
            .newBuilder(ErrorCode.UNIMPLEMENTED)
            .withMessage(
                "This broker fills only what the market takes the moment an order is posted, and this one has to wait"
            )
            .build();
    }

    /**
     * Nothing to do. Without a clock the remainder can never fill, but it is not refused either -
     * the order simply stays PARTIALLYFILL and keeps showing up among the active ones, which is
     * exactly what "still working" means to a broker where time does not pass. The account holder
     * can cancel it.
     */
    @Override
    public void onPartiallyFilled(Order order) {
    }

    @Override
    public void onCancelled(Order order) {
        // Nothing was ever scheduled or reserved, so there is nothing to undo.
    }
}
