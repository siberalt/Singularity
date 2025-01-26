package com.siberalt.singularity.broker.impl.mock.shared.order;

import com.siberalt.singularity.simulation.Event;

public class OrderEvent {
    protected Order order;
    protected Event event;

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
}
