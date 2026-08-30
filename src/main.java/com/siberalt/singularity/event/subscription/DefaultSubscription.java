package com.siberalt.singularity.event.subscription;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class DefaultSubscription implements Subscription {
    private volatile boolean active;
    private final Runnable onUnsubscribe;
    private final List<Throwable> errors = new CopyOnWriteArrayList<>();

    public DefaultSubscription(boolean active, Runnable onUnsubscribe) {
        this.active = active;
        this.onUnsubscribe = onUnsubscribe;
    }

    public DefaultSubscription(boolean active) {
        this(active, () -> {});
    }

    public void addError(Throwable error) {
        errors.add(error);
    }

    @Override
    public void stop() {
        onUnsubscribe.run();
        this.active = false;
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public List<Throwable> getErrors() {
        return Collections.unmodifiableList(errors);
    }
}
