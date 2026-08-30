package com.siberalt.singularity.strategy;

import com.siberalt.singularity.strategy.observer.Observer;

public interface Strategy {
    void run(Observer observer);

    /**
     * Stops the strategy from reacting to further events. Implementations are not expected to
     * close any open position or cancel outstanding orders - it only unsubscribes.
     */
    void stop();
}
