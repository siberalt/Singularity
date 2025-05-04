package com.siberalt.singularity.strategy;

import java.util.HashMap;
import java.util.Map;

public class StrategyManager {
    protected Map<String, StrategyInfo> strategies = new HashMap<>();

    public void add(StrategyInfo strategyInfo) {
        strategies.put(strategyInfo.getId(), strategyInfo);
    }

    public StrategyInfo get(String id) {
        return strategies.get(id);
    }

    public void remove(String id) {
        strategies.remove(id);
    }

    public void clearStrategies() {
        strategies.clear();
    }

    private StrategyInfo getStrategyOrThrow(String id) {
        StrategyInfo strategy = strategies.get(id);
        if (strategy == null) {
            throw new IllegalArgumentException("Strategy with ID " + id + " does not exist.");
        }
        return strategy;
    }
}