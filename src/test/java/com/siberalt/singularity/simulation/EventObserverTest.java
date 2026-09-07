package com.siberalt.singularity.simulation;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventObserverTest {
    private static final Instant START = Instant.parse("2021-01-01T10:00:00Z");

    private final EventObserver observer = new EventObserver();
    private final EventInvoker invoker = observer -> {
    };

    @Test
    void handsOutTheEarliestMomentFirst() {
        observer.scheduleEvent(Event.create(START.plusSeconds(120), invoker));
        observer.scheduleEvent(Event.create(START.plusSeconds(60), invoker));

        assertEquals(START.plusSeconds(60), observer.getNextEventTime());

        observer.advanceToNextEvent();

        assertEquals(START.plusSeconds(120), observer.getNextEventTime());

        observer.advanceToNextEvent();

        assertFalse(observer.hasUpcomingEvents());
    }

    /**
     * The reason the whole guard exists: the next moment handed out is the earliest one held, so an
     * event booked behind the simulation would be returned as "next" and pull the clock back to it,
     * replaying every unit against a world that has already moved past.
     */
    @Test
    void refusesAnEventBehindTheMomentAlreadyReached() {
        observer.scheduleEvent(Event.create(START.plusSeconds(60), invoker));
        observer.advanceToNextEvent();

        IllegalArgumentException refused = assertThrows(
            IllegalArgumentException.class,
            () -> observer.scheduleEvent(Event.create(START.plusSeconds(30), invoker))
        );

        assertTrue(refused.getMessage().contains("backwards"));
        assertFalse(observer.hasUpcomingEvents());
    }

    /**
     * The moment being dispatched is not behind anything - an event booked for it belongs to the
     * work in progress, which is how an order that fills within the bar it was placed in gets to
     * happen at all.
     */
    @Test
    void acceptsAnEventOnTheMomentBeingDispatched() {
        observer.scheduleEvent(Event.create(START.plusSeconds(60), invoker));
        observer.advanceToNextEvent();

        observer.scheduleEvent(Event.create(START.plusSeconds(60), invoker));

        assertTrue(observer.hasUpcomingEvents());
        assertEquals(START.plusSeconds(60), observer.getNextEventTime());
    }

    @Test
    void treatsTheStartOfARunAsAlreadyReached() {
        observer.startAt(START);

        assertEquals(START, observer.getReachedTime());
        assertThrows(
            IllegalArgumentException.class,
            () -> observer.scheduleEvent(Event.create(START.minus(Duration.ofDays(1)), invoker))
        );

        // The start itself is fair game - units book their first event there while initialising.
        observer.scheduleEvent(Event.create(START, invoker));

        assertEquals(START, observer.getNextEventTime());
    }

    @Test
    void bookingIsUnrestrictedUntilARunSaysWhereItStarts() {
        // Nothing has been reached yet, so a fresh observer takes whatever it is given.
        observer.scheduleEvent(Event.create(Instant.parse("1997-05-02T08:10:00Z"), invoker));

        assertTrue(observer.hasUpcomingEvents());
    }

    @Test
    void cancellingTheOnlyEventOfAMomentDropsTheMomentItself() {
        Event event = Event.create(START.plusSeconds(60), invoker);

        observer.scheduleEvent(event);
        observer.cancelEvent(event);

        assertFalse(observer.hasUpcomingEvents());
    }
}
