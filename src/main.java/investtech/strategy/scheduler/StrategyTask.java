package investtech.strategy.scheduler;

import investtech.strategy.StrategyInterface;
import investtech.strategy.context.AbstractContext;

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
