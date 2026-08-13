package com.siberalt.singularity.strategy.observer;

import com.siberalt.singularity.strategy.Strategy;

import java.util.ArrayList;
import java.util.List;

public class Observer {
    // This class can be extended to implement specific observer functionality
    // For example, it could be used to observe changes in a strategy or event
    // and react accordingly. Currently, it serves as a base class for observers.

    private final List<Strategy> strategies = new ArrayList<>();

    public void addStrategy(Strategy strategy) {
        strategies.add(strategy);
    }

    public void removeStrategy(Strategy strategy) {
        strategies.remove(strategy);
    }

    public List<Strategy> getStrategies() {
        return new ArrayList<>(strategies);
    }

    public void notifyFinish(Strategy strategy) {
        // Notify observers that a strategy has finished execution
        // This can be overridden in subclasses to provide specific behavior
        System.out.println("Strategy finished: " + strategy);
    }
}
