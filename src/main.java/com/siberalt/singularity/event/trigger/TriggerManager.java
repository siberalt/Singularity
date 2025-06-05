package com.siberalt.singularity.event.trigger;

import com.siberalt.singularity.event.EventDispatcher;
import com.siberalt.singularity.event.subscription.SubscriptionSpec;

public interface TriggerManager {
    void enable(SubscriptionSpec<?> subscriptionSpec, EventDispatcher eventDispatcher);

    void disable(SubscriptionSpec<?> subscriptionSpec);

    boolean isEnabled(SubscriptionSpec<?> subscriptionSpec);
}
