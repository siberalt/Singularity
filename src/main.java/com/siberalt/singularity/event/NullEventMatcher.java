package com.siberalt.singularity.event;

import com.siberalt.singularity.event.subscription.SubscriptionSpec;

/**
 * Default {@link EventMatcher} used until {@link EventManager#setEventMatcher} is called.
 * Applies no extra filtering beyond the event-type check {@link EventManager} already performs,
 * so the manager is usable out of the box instead of NPE-ing on the first dispatch.
 */
class NullEventMatcher implements EventMatcher {
    @Override
    public boolean matches(SubscriptionSpec<?> subscriptionSpec, Event event) {
        return true;
    }
}
