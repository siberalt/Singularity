package com.siberalt.singularity.simulation;

import java.time.Instant;
import java.util.*;

public class EventObserver {
    protected SortedMap<Instant, List<Event>> eventsSortedByTime = new TreeMap<>(
            Comparator.comparingLong(Instant::toEpochMilli)
    );

    public void scheduleEvent(Event event) {
        var timePoint = event.getTimePoint();

        if (!eventsSortedByTime.containsKey(timePoint)) {
            eventsSortedByTime.put(timePoint, new ArrayList<>(List.of(event)));
        } else {
            eventsSortedByTime.get(timePoint).add(event);
        }
    }

    public void cancelEvent(Event event) {
        var timePoint = event.getTimePoint();

        if (eventsSortedByTime.containsKey(timePoint)) {
            var events = eventsSortedByTime.get(timePoint);
            events.remove(event);

            if (events.isEmpty()) {
                eventsSortedByTime.remove(timePoint);
            }
        }
    }

    public void advanceToNextEvent() {
        eventsSortedByTime.remove(getNextEventTime());
    }

    public Instant getNextEventTime() {
        return eventsSortedByTime.firstKey();
    }

    public List<Event> getNextEvents() {
        var timePoint = eventsSortedByTime.firstKey();

        return eventsSortedByTime.get(timePoint);
    }

    public boolean hasUpcomingEvents() {
        return !eventsSortedByTime.isEmpty();
    }
}
