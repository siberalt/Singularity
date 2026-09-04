package com.siberalt.singularity.broker.impl.mock.shared.order;

import com.siberalt.singularity.broker.contract.value.money.Money;
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
    protected Money blockedMoney;
    protected long blockedLots;

    public OrderEvent(Order order, Event event) {
        this.order = order;
        this.event = event;
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
