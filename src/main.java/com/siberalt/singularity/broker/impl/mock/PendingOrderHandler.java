package com.siberalt.singularity.broker.impl.mock;

import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.entity.order.Order;

import java.time.Instant;

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
     * Called instead of a fill when the order is not trading now. The order has been priced but
     * nothing has been stored or charged yet; an implementation either rejects it or takes over
     * responsibility for storing and later filling it.
     * <p>
     * Why it is waiting is the caller's business and shows only in {@code tradableFrom}: the market
     * did not meet its price, in which case it may trade from this moment on, or it has not reached
     * the exchange yet, in which case it may not - what the market does before an order gets there
     * is not its to take.
     *
     * @param tradableFrom the earliest moment this order may trade at
     */
    void onPending(Order order, Instant tradableFrom) throws AbstractException;

    /**
     * Called after a fill that could only take part of the order, because the market did not have
     * the volume for the rest of it. The lots that did fill are already paid for and journalled;
     * the order carries the running totals and is stored as PARTIALLYFILL. What happens to the
     * remainder is the implementation's business - it either keeps working or it does not.
     */
    void onPartiallyFilled(Order order) throws AbstractException;

    /**
     * Called after a stored, not-yet-filled order is cancelled, so an implementation that
     * scheduled a future fill can drop it and release whatever it reserved for it. Orders that
     * filled immediately never reach here.
     */
    void onCancelled(Order order) throws AbstractException;
}
