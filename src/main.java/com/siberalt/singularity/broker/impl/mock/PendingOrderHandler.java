package com.siberalt.singularity.broker.impl.mock;

import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.entity.order.Order;

/**
 * What the broker does with an order it cannot fill at the current price - the one behaviour that
 * separates a plain mock broker from an event-simulated one. Splitting it out is what lets both
 * share a single {@link MockOrderService} instead of one subclassing the other.
 *
 * @see RejectingPendingOrderHandler
 * @see SimulatedPendingOrderHandler
 */
public interface PendingOrderHandler {
    /**
     * Called instead of a fill when the order's limit price is not met by the current market
     * price. The order has been priced but nothing has been stored or charged yet; an
     * implementation either rejects it or takes over responsibility for storing and later filling
     * it.
     */
    void onNotFillable(Order order) throws AbstractException;

    /**
     * Called after a stored, not-yet-filled order is cancelled, so an implementation that
     * scheduled a future fill can drop it and release whatever it reserved for it. Orders that
     * filled immediately never reach here.
     */
    void onCancelled(Order order) throws AbstractException;
}
