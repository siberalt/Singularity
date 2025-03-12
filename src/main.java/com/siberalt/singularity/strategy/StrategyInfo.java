package com.siberalt.singularity.strategy;

public class StrategyInfo {
    protected String id;
    protected StrategyInterface strategy;
    protected StrategyState strategyState;

    public StrategyInfo(String id, StrategyInterface strategy) {
        this.id = id;
        this.strategy = strategy;
    }

    public StrategyInterface getStrategy() {
        return strategy;
    }

    public StrategyState getStrategyState() {
        return strategyState;
    }

    public StrategyInfo setStrategyState(StrategyState strategyState) {
        this.strategyState = strategyState;
        return this;
    }

    public String getId() {
        return id;
    }
}
