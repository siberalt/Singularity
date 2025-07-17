package com.siberalt.singularity.event.subscription;

import java.util.List;

public interface Subscription {
    void stop();

    boolean isActive();

    List<Throwable> getErrors();
}
