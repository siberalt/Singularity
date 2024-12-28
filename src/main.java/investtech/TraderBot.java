package investtech;

import investtech.broker.contract.execution.BrokerInterface;
import investtech.configuration.ConfigurationInterface;
import investtech.factory.ServiceContainer;
import investtech.factory.broker.BrokerFactoryManager;
import investtech.factory.broker.TinkoffBrokerFactory;
import investtech.factory.exception.FactoryNotFoundException;
import investtech.factory.strategy.StrategyFactoryManager;
import investtech.factory.strategy.TinkoffIMOEXStrategyFactory;
import investtech.strategy.StrategyInterface;
import investtech.strategy.StrategyExecutor;
import investtech.strategy.scheduler.Scheduler;

import java.util.HashMap;

public class TraderBot {
    protected ConfigurationInterface configuration;

    protected ServiceContainer serviceContainer;

    protected StrategyFactoryManager strategyFactoryManager;

    protected BrokerFactoryManager brokerFactoryManager;

    public TraderBot(ConfigurationInterface configuration) {
        this.configuration = configuration;
    }

    public void run() throws FactoryNotFoundException {
        init();

        String[] activeBrokerIds = (String[]) configuration.get("run.activeBrokerIds");
        String[] activeStrategyIds = (String[]) configuration.get("run.activeStrategies");
        HashMap<String, StrategyInterface> strategies = new HashMap<>();
        HashMap<String, BrokerInterface> brokers = new HashMap<>();

        for (String brokerId : activeBrokerIds) {
            serviceContainer.addFactory(brokerId, brokerFactoryManager);
            brokers.put(brokerId, (BrokerInterface) serviceContainer.get(brokerId));
        }

        for (String strategyId : activeStrategyIds) {
            serviceContainer.addFactory(strategyId, strategyFactoryManager);
            strategies.put(strategyId, (StrategyInterface) serviceContainer.get(strategyId));
        }

        StrategyExecutor strategyExecutor = new StrategyExecutor(strategies, brokers, new Scheduler());
        strategyExecutor.run();
    }

    public void emulate() {

    }

    protected void init() {
        strategyFactoryManager = new StrategyFactoryManager()
                .registerType("tinkoff_imoex", new TinkoffIMOEXStrategyFactory());

        brokerFactoryManager = new BrokerFactoryManager()
                .registerType("tinkoff", new TinkoffBrokerFactory());

        serviceContainer = new ServiceContainer(configuration)
                .setConfigMappings(
                        new String[]{
                                "run.strategies",
                                "run.brokers",
                                "emulate.strategies",
                                "emulate.brokers"
                        }
                );
    }
}
