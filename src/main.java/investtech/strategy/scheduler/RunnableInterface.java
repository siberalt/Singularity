package investtech.strategy.scheduler;

import investtech.shared.IdentifiableInterface;
import investtech.strategy.context.AbstractContext;

public interface RunnableInterface extends IdentifiableInterface {
    void run(AbstractContext<?> context);
}
