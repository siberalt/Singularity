package investtech.strategy.event;

import investtech.strategy.context.AbstractContext;

public interface EventHandlerInterface {
    void handle(Event event, AbstractContext<?> context);
}
