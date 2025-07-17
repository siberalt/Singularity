package com.siberalt.singularity.broker.contract.execution;

import com.siberalt.singularity.event.subscription.SubscriptionManager;

public interface EventSubscriptionBroker extends Broker {
    /**
     * Gets the event dispatcher.
     *
     * @return the event dispatcher
     */
    SubscriptionManager getSubscriptionManager();
}
