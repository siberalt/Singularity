package com.siberalt.singularity.broker.shared;

import com.siberalt.singularity.broker.contract.execution.EventSubscriptionBroker;
import com.siberalt.singularity.event.Event;
import com.siberalt.singularity.event.EventHandler;
import com.siberalt.singularity.event.subscription.SubscriptionSpec;

public class EventSubscriptionBrokerFacade extends BrokerFacade{
    private final EventSubscriptionBroker broker;

    public EventSubscriptionBrokerFacade(EventSubscriptionBroker broker) {
        super(broker);
        this.broker = broker;
    }

    public <T extends Event> void subscribe(SubscriptionSpec<T> subscriptionSpec, EventHandler<T> handler) {
        broker.getSubscriptionManager().subscribe(subscriptionSpec, handler);
    }
}
