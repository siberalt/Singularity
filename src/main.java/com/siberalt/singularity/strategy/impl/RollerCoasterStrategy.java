package com.siberalt.singularity.strategy.impl;

import com.siberalt.singularity.strategy.StrategyInterface;
import com.siberalt.singularity.strategy.context.AbstractContext;
import com.siberalt.singularity.strategy.context.ContextAware;
import com.siberalt.singularity.strategy.observer.Observer;

public class RollerCoasterStrategy implements StrategyInterface, ContextAware {
    private AbstractContext<?> context;
    private double sellDelta = 0.03;
    private double buyDelta = 0.03;

    @Override
    public void applyContext(AbstractContext<?> context) {
        this.context = context;
    }

    @Override
    public void run(Observer observer) {

    }

    public double getSellDelta() {
        return sellDelta;
    }

    public RollerCoasterStrategy setSellDelta(double sellDelta) {
        this.sellDelta = sellDelta;
        return this;
    }

    public double getBuyDelta() {
        return buyDelta;
    }

    public RollerCoasterStrategy setBuyDelta(double buyDelta) {
        this.buyDelta = buyDelta;
        return this;
    }
}
