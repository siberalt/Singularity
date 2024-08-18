package investtech.emulation;

import java.time.Instant;
import java.util.*;

public class EventObserver {
    protected SortedMap<Instant, List<Event>> eventsSortedByTime = new TreeMap<>(
            Comparator.comparingLong(Instant::toEpochMilli).reversed()
    );

    public void plan(Event event) {
        var timePoint = event.getTimePoint();

        if (!eventsSortedByTime.containsKey(timePoint)) {
            eventsSortedByTime.put(timePoint, new ArrayList<>(List.of(event)));
        } else {
            eventsSortedByTime.get(timePoint).add(event);
        }
    }

    public void cancel(Event event) {
        var timePoint = event.getTimePoint();

        if (eventsSortedByTime.containsKey(timePoint)) {
            var events = eventsSortedByTime.get(timePoint);
            events.remove(event);

            if (events.isEmpty()) {
                eventsSortedByTime.remove(timePoint);
            }
        }
    }

    public void rewindToNextEvents() {
        eventsSortedByTime.remove(getComingEventsTimePoint());
    }

    public Instant getComingEventsTimePoint() {
        return eventsSortedByTime.firstKey();
    }

    public List<Event> getNextComingEvents() {
        var timePoint = eventsSortedByTime.firstKey();

        return eventsSortedByTime.get(timePoint);
    }

    public boolean hasComingEvents() {
        return !eventsSortedByTime.isEmpty();
    }
}
