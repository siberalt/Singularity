package investtech.simulation;

import java.time.Instant;
import java.util.UUID;

public class Event {
    protected String id;

    protected Instant timePoint;

    protected EventInvokerInterface eventInvoker;

    private Event(String id, Instant timePoint, EventInvokerInterface eventInvoker) {
        this.id = id;
        this.timePoint = timePoint;
        this.eventInvoker = eventInvoker;
    }

    public String getId() {
        return id;
    }

    public Instant getTimePoint() {
        return timePoint;
    }


    public EventInvokerInterface getEventInvoker() {
        return eventInvoker;
    }

    public static Event create(Instant timePoint, EventInvokerInterface eventInvoker) {
        return new Event(UUID.randomUUID().toString(), timePoint, eventInvoker);
    }
}
