package com.siberalt.singularity.event;

import com.siberalt.singularity.event.subscription.SubscriptionSpec;

public interface EventMatcher {

    /**
     * Checks if the event matches the subscription specification.
     *
     * @param subscriptionSpec the subscription specification
     * @param event            the event to check
     * @return true if the event matches the subscription specification, false otherwise
     */
    boolean matches(SubscriptionSpec<?> subscriptionSpec, Event event);
}
