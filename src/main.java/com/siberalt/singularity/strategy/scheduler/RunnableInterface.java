package com.siberalt.singularity.strategy.scheduler;

import com.siberalt.singularity.strategy.context.AbstractContext;
import com.siberalt.singularity.shared.IdentifiableInterface;

public interface RunnableInterface extends IdentifiableInterface {
    void run(AbstractContext<?> context);
}
