package com.siberalt.singularity.event.subscription;

import java.util.List;

public class DefaultSubscription implements Subscription {
    private boolean active;
    private Runnable onUnsubscribe;
    private List<Throwable> errors;

    public DefaultSubscription(boolean active, Runnable onUnsubscribe) {
        this.active = active;
        this.onUnsubscribe = onUnsubscribe;
    }

    public DefaultSubscription(boolean active) {
        this.active = active;
    }

    public DefaultSubscription setErrors(List<Throwable> errors) {
        this.errors = errors;
        return this;
    }

    @Override
    public void unsubscribe() {
        onUnsubscribe.run();
        this.active = false;
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public List<Throwable> getErrors() {
        return errors;
    }
}
