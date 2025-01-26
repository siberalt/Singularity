package com.siberalt.singularity.strategy.scheduler;

import com.siberalt.singularity.strategy.context.AbstractContext;
import com.siberalt.singularity.strategy.StrategyInterface;

public class StrategyTask implements Runnable {
    protected AbstractContext<?> context;

    protected StrategyInterface strategy;

    public StrategyTask(AbstractContext<?> context, StrategyInterface strategy){
        this.context = context;
        this.strategy = strategy;
    }

    public StrategyInterface getStrategy() {
        return strategy;
    }

    public void run() {
        this.strategy.run(context);
    }
}
