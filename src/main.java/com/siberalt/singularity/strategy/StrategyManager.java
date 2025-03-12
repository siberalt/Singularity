package com.siberalt.singularity.strategy;

import java.io.IOException;
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

    public void start(String id) {
        StrategyInfo strategy = getStrategyOrThrow(id);
        strategy.getStrategy().execute(StrategyCommand.START);
    }

    public void start() {
        for (StrategyInfo strategy : strategies.values()) {
            strategy.getStrategy().execute(StrategyCommand.START);
        }
    }

    public void stop(String id) {
        StrategyInfo strategy = getStrategyOrThrow(id);
        strategy.getStrategy().execute(StrategyCommand.STOP);
    }

    public void pause(String id) {
        StrategyInfo strategy = getStrategyOrThrow(id);
        strategy.getStrategy().execute(StrategyCommand.PAUSE);
    }

    public void resume(String id) {
        StrategyInfo strategy = getStrategyOrThrow(id);
        strategy.getStrategy().execute(StrategyCommand.RESUME);
    }

    public void restart(String id) throws IOException {

    }

    public void shutdown(String id) {
        StrategyInfo strategy = getStrategyOrThrow(id);
        strategy.getStrategy().execute(StrategyCommand.SHUTDOWN);
    }

    private StrategyInfo getStrategyOrThrow(String id) {
        StrategyInfo strategy = strategies.get(id);
        if (strategy == null) {
            throw new IllegalArgumentException("Strategy with ID " + id + " does not exist.");
        }
        return strategy;
    }
}