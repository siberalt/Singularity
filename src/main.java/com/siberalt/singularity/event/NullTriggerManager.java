package com.siberalt.singularity.event;

import com.siberalt.singularity.event.subscription.SubscriptionSpec;
import com.siberalt.singularity.event.trigger.TriggerManager;

class NullTriggerManager implements TriggerManager {
    @Override
    public void enable(SubscriptionSpec<?> subscriptionSpec, EventDispatcher eventDispatcher) {

    }

    @Override
    public void disable(SubscriptionSpec<?> subscriptionSpec) {

    }

    @Override
    public boolean isEnabled(SubscriptionSpec<?> subscriptionSpec) {
        return false;
    }
}
