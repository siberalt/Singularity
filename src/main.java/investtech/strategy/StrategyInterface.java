package investtech.strategy;

import investtech.shared.IdentifiableInterface;
import investtech.strategy.context.AbstractContext;

public interface StrategyInterface extends IdentifiableInterface {
    void start(AbstractContext<?> context);

    void run(AbstractContext<?> context);

    void stop(AbstractContext<?> context);
}
