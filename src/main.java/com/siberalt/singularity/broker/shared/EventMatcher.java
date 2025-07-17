package com.siberalt.singularity.broker.shared;

import com.siberalt.singularity.broker.contract.service.event.dispatcher.events.NewCandleEvent;
import com.siberalt.singularity.broker.contract.service.event.dispatcher.subscriptions.NewCandleSubscriptionSpec;
import com.siberalt.singularity.event.Event;
import com.siberalt.singularity.event.subscription.SubscriptionSpec;

public class EventMatcher implements com.siberalt.singularity.event.EventMatcher {
    @Override
    public boolean matches(SubscriptionSpec<?> subscriptionSpec, Event event) {
        if (subscriptionSpec == null || event == null) {
            return false;
        }

        // Check if the event type matches the subscription spec's event type
        if (!subscriptionSpec.getEventType().isAssignableFrom(event.getClass())) {
            return false;
        }

        if (subscriptionSpec instanceof NewCandleSubscriptionSpec newCandleSpec) {
            // Additional checks for NewCandleSubscriptionSpec
            if (!(event instanceof NewCandleEvent newCandleEvent)) {
                return false;
            }
            // Check if the instrument ID is in the subscription spec
            return newCandleSpec.getInstrumentIds().contains(newCandleEvent.getCandle().getInstrumentUid());
        }

        return true; // If no specific checks are needed, return true
    }
}
