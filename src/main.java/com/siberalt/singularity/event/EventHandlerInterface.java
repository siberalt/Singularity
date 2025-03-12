package com.siberalt.singularity.event;

import com.siberalt.singularity.strategy.context.AbstractContext;

public interface EventHandlerInterface {
    void handle(Event event, AbstractContext<?> context);
}
