package com.siberalt.singularity.strategy;

import com.siberalt.singularity.strategy.context.AbstractContext;
import com.siberalt.singularity.shared.IdentifiableInterface;

public interface StrategyInterface extends IdentifiableInterface {
    void start(AbstractContext<?> context);

    void run(AbstractContext<?> context);

    void stop(AbstractContext<?> context);
}
