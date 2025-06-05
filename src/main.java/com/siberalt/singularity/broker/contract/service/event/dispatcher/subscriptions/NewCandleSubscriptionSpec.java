package com.siberalt.singularity.broker.contract.service.event.dispatcher.subscriptions;

import com.siberalt.singularity.broker.contract.service.event.dispatcher.events.NewCandleEvent;
import com.siberalt.singularity.event.subscription.SubscriptionSpec;

import java.util.Set;

public class NewCandleSubscriptionSpec extends SubscriptionSpec<NewCandleEvent> {
    private final Set<String> instrumentIds;

    public NewCandleSubscriptionSpec(Set<String> instrumentIds) {
        super(NewCandleEvent.class);
        this.instrumentIds = instrumentIds;
    }

    public Set<String> getInstrumentIds() {
        return instrumentIds;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        NewCandleSubscriptionSpec that = (NewCandleSubscriptionSpec) obj;
        return getEventType().equals(that.getEventType()) &&
            instrumentIds.equals(that.instrumentIds);
    }

    @Override
    public int hashCode() {
        int result = getEventType().hashCode();
        result = 31 * result + instrumentIds.hashCode();
        return result;
    }
}
