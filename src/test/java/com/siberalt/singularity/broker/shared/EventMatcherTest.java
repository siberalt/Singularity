package com.siberalt.singularity.broker.shared;

import com.siberalt.singularity.broker.contract.service.event.dispatcher.events.NewCandleEvent;
import com.siberalt.singularity.broker.contract.service.event.dispatcher.subscriptions.NewCandleSubscriptionSpec;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.event.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventMatcherTest {

    @Mock
    private NewCandleSubscriptionSpec newCandleSubscriptionSpec;

    @Mock
    private NewCandleEvent newCandleEvent;

    @Mock
    private Event genericEvent;

    private EventMatcher eventMatcher;

    @BeforeEach
    void setUp() {
        eventMatcher = new EventMatcher();
    }

    @Test
    void matchesReturnsFalseWhenSubscriptionSpecIsNull() {
        assertFalse(eventMatcher.matches(null, newCandleEvent));
    }

    @Test
    void matchesReturnsFalseWhenEventIsNull() {
        assertFalse(eventMatcher.matches(newCandleSubscriptionSpec, null));
    }

    @Test
    void matchesReturnsFalseWhenEventTypeDoesNotMatchSubscriptionSpec() {
        when(newCandleSubscriptionSpec.getEventType()).thenReturn(NewCandleEvent.class);
        assertFalse(eventMatcher.matches(newCandleSubscriptionSpec, genericEvent));
    }

    @Test
    void matchesReturnsFalseWhenInstrumentIdNotInSubscriptionSpec() {
        when(newCandleSubscriptionSpec.getEventType()).thenReturn(NewCandleEvent.class);
        when(newCandleSubscriptionSpec.getInstrumentIds()).thenReturn(Set.of("instrument1"));
        when(newCandleEvent.getCandle()).thenReturn(mock(Candle.class));
        when(newCandleEvent.getCandle().getInstrumentUid()).thenReturn("instrument2");

        assertFalse(eventMatcher.matches(newCandleSubscriptionSpec, newCandleEvent));
    }

    @Test
    void matchesReturnsTrueWhenAllConditionsAreMet() {
        when(newCandleSubscriptionSpec.getEventType()).thenReturn(NewCandleEvent.class);
        when(newCandleSubscriptionSpec.getInstrumentIds()).thenReturn(Set.of("instrument1"));
        when(newCandleEvent.getCandle()).thenReturn(mock(Candle.class));
        when(newCandleEvent.getCandle().getInstrumentUid()).thenReturn("instrument1");

        assertTrue(eventMatcher.matches(newCandleSubscriptionSpec, newCandleEvent));
    }
}

