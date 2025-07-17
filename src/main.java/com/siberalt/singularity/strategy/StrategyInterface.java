package com.siberalt.singularity.strategy;

import com.siberalt.singularity.strategy.observer.Observer;

public interface StrategyInterface {
    //void execute(StrategyCommand command);
    void run(Observer observer);
}
