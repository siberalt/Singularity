package com.siberalt.singularity.strategy;

import com.siberalt.singularity.strategy.observer.Observer;

public interface Strategy {
    void run(Observer observer);
}
