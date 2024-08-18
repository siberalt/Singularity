package investtech.broker.impl.tinkoff;

import investtech.strategy.context.AbstractContext;
import investtech.strategy.event.Event;
import investtech.strategy.event.EventHandlerInterface;
import investtech.strategy.event.EventManagerInterface;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EventManager implements EventManagerInterface {
    protected AbstractContext<?> context;

    protected Map<String, List<EventHandlerInterface>> eventHandlers = new HashMap<>();

    @Override
    public void attach(String eventId, EventHandlerInterface handler) {
        eventHandlers.computeIfAbsent(eventId, x -> new ArrayList<>()).add(handler);
    }

    @Override
    public void detach(String eventId, EventHandlerInterface handler) {
        eventHandlers.get(eventId).removeIf(x -> x.equals(handler));
    }

    @Override
    public void trigger(String eventId, Event event) {
        if (eventHandlers.containsKey(eventId)) {
            for (EventHandlerInterface handler : eventHandlers.get(eventId)) {
                handler.handle(event, context);
            }
        }
    }
}
