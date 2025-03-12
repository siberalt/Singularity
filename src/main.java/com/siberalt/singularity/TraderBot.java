package com.siberalt.singularity;

import com.siberalt.singularity.broker.contract.execution.BrokerInterface;
import com.siberalt.singularity.configuration.ConfigInterface;
import com.siberalt.singularity.service.ServiceRegistry;
import com.siberalt.singularity.service.exception.FactoryNotFoundException;
import com.siberalt.singularity.service.factory.broker.BrokerFactoryManager;
import com.siberalt.singularity.service.factory.strategy.StrategyFactoryManager;
import com.siberalt.singularity.strategy.StrategyInterface;

import java.util.HashMap;

public class TraderBot {
    protected ConfigInterface configuration;

    protected ServiceRegistry serviceContainer;

    protected StrategyFactoryManager strategyFactoryManager;

    protected BrokerFactoryManager brokerFactoryManager;

    public TraderBot(ConfigInterface configuration) {
        this.configuration = configuration;
    }

    public void run() throws FactoryNotFoundException {
        init();

        String[] activeBrokerIds = (String[]) configuration.get("run.activeBrokerIds");
        String[] activeStrategyIds = (String[]) configuration.get("run.activeStrategies");
        HashMap<String, StrategyInterface> strategies = new HashMap<>();
        HashMap<String, BrokerInterface> brokers = new HashMap<>();

        for (String brokerId : activeBrokerIds) {
            serviceContainer.setFactory(brokerId, brokerFactoryManager);
            brokers.put(brokerId, (BrokerInterface) serviceContainer.get(brokerId));
        }

        for (String strategyId : activeStrategyIds) {
            serviceContainer.setFactory(strategyId, strategyFactoryManager);
            strategies.put(strategyId, (StrategyInterface) serviceContainer.get(strategyId));
        }

        //StrategyExecutor strategyExecutor = new StrategyExecutor(strategies, brokers, new Scheduler());
        //strategyExecutor.run();
    }

    public void emulate() {

    }

    protected void init() {

    }
}
