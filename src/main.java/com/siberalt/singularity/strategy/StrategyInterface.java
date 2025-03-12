package com.siberalt.singularity.strategy;

import com.siberalt.singularity.strategy.context.ContextAwareInterface;

public interface StrategyInterface extends ContextAwareInterface {
    void execute(StrategyCommand command);
}
