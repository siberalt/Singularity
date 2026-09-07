package com.siberalt.singularity.broker.impl.mock.shared.order;

import com.siberalt.singularity.broker.contract.value.money.Money;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.order.Order;
import com.siberalt.singularity.simulation.Event;

/**
 * A parked order and the simulation event that will resolve it, together with whatever was
 * reserved on the account to cover it. The reservation is recorded here because the fill price is
 * not known when the order is parked - by the time the event comes due the order carries the
 * price it actually filled at, so the amount to release can no longer be recomputed from it.
 */
public class OrderEvent {
    protected Order order;
    protected Event event;
    protected Candle bar;
    protected Money blockedMoney;
    protected long blockedLots;

    public OrderEvent(Order order, Event event, Candle bar) {
        this.order = order;
        this.event = event;
        this.bar = bar;
    }

    /**
     * The bar this event trades against, or {@code null} when the event ends the order instead of
     * filling it. Carried from the moment the event was booked rather than looked up again when it
     * comes due - it is the same bar either way, and a simulation fills often enough for the extra
     * query to be worth avoiding.
     * <p>
     * How much it trades is deliberately not recorded: the bar is shared with every other order
     * working against the same instrument, so what is left of it is only known once the moment
     * arrives. An order too large for one bar is resolved by a chain of these, each taking what its
     * own bar still had.
     */
    public Candle getBar() {
        return bar;
    }

    public boolean fills() {
        return bar != null;
    }

    public OrderEvent setBar(Candle bar) {
        this.bar = bar;
        return this;
    }

    public Order getOrder() {
        return order;
    }

    public OrderEvent setOrder(Order order) {
        this.order = order;
        return this;
    }

    public Event getEvent() {
        return event;
    }

    public OrderEvent setEvent(Event event) {
        this.event = event;
        return this;
    }

    /**
     * Money reserved for a parked buy order, or {@code null} when nothing was reserved.
     */
    public Money getBlockedMoney() {
        return blockedMoney;
    }

    public OrderEvent setBlockedMoney(Money blockedMoney) {
        this.blockedMoney = blockedMoney;
        return this;
    }

    /**
     * Instrument lots reserved for a parked sell order, or {@code 0} when nothing was reserved.
     */
    public long getBlockedLots() {
        return blockedLots;
    }

    public OrderEvent setBlockedLots(long blockedLots) {
        this.blockedLots = blockedLots;
        return this;
    }
}
