package com.siberalt.singularity.simulation;

import java.time.Instant;
import java.util.*;

/**
 * The simulation's pending events, ordered by the moment they come due. The simulator empties it
 * one moment at a time, and that is what drives the clock forward.
 * <p>
 * Forward is the whole point: the next moment handed out is the earliest one held, so an event
 * booked for a moment already gone would be handed back as "next" and drag the clock into the past
 * with it. Everything that has already run at the moments in between would then run again, against
 * a world that has moved on. So the observer remembers how far it has got and refuses anything
 * behind that, rather than letting the mistake travel to whoever reads the clock next.
 * <p>
 * The current moment itself is still open for booking: an event scheduled for the moment being
 * dispatched belongs to the work in progress and is picked up by it.
 */
public class EventObserver {
    protected SortedMap<Instant, List<Event>> eventsSortedByTime = new TreeMap<>(
            Comparator.comparingLong(Instant::toEpochMilli)
    );

    /**
     * How far the simulation has got. Before a run says otherwise nothing is out of bounds, so that
     * events can be booked while the units are still being wired up.
     */
    protected Instant reachedTime = Instant.MIN;

    /**
     * Fixes the moment a run begins, so that anything booked before it is caught as the mistake it
     * is rather than silently pulling the clock back past the start.
     */
    public synchronized void startAt(Instant startTime) {
        reachedTime = startTime;
    }

    public synchronized Instant getReachedTime() {
        return reachedTime;
    }

    public synchronized void scheduleEvent(Event event) {
        var timePoint = event.getTimePoint();

        if (timePoint.isBefore(reachedTime)) {
            throw new IllegalArgumentException(
                String.format(
                    "Event %s is scheduled for %s, which the simulation passed at %s. "
                        + "Booking it would take the clock backwards and replay what has already run.",
                    event.getId(),
                    timePoint,
                    reachedTime
                )
            );
        }

        if (!eventsSortedByTime.containsKey(timePoint)) {
            eventsSortedByTime.put(timePoint, new ArrayList<>(List.of(event)));
        } else {
            eventsSortedByTime.get(timePoint).add(event);
        }
    }

    public synchronized void cancelEvent(Event event) {
        var timePoint = event.getTimePoint();

        if (eventsSortedByTime.containsKey(timePoint)) {
            var events = eventsSortedByTime.get(timePoint);
            events.remove(event);

            if (events.isEmpty()) {
                eventsSortedByTime.remove(timePoint);
            }
        }
    }

    /**
     * Moves to the earliest moment held, dropping the events due there - the caller has the time
     * from {@link #getNextEventTime} and is about to run them. Nothing may be booked before this
     * moment afterwards.
     */
    public synchronized void advanceToNextEvent() {
        reachedTime = getNextEventTime();
        eventsSortedByTime.remove(reachedTime);
    }

    public synchronized Instant getNextEventTime() {
        return eventsSortedByTime.firstKey();
    }

    public synchronized List<Event> getNextEvents() {
        var timePoint = eventsSortedByTime.firstKey();

        return eventsSortedByTime.get(timePoint);
    }

    public synchronized boolean hasUpcomingEvents() {
        return !eventsSortedByTime.isEmpty();
    }
}
