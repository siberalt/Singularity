package com.siberalt.singularity.event;

import com.siberalt.singularity.event.subscription.Subscription;
import com.siberalt.singularity.event.subscription.SubscriptionSpec;
import com.siberalt.singularity.event.trigger.TriggerManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
class EventManagerTest {
    static class SomeOtherEvent extends Event {
        public SomeOtherEvent(UUID id) {
            super(id);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof SomeOtherEvent;
        }

        @Override
        public int hashCode() {
            return SomeOtherEvent.class.hashCode();
        }
    }

    @Mock
    private TriggerManager triggerManager;

    @Mock
    private EventMatcher eventMatcher;

    @Mock
    private EventHandler<Event> handler;

    private EventManager eventManager;

    @BeforeEach
    void setUp() {
        eventManager = new EventManager(Executors.newSingleThreadExecutor(), Set.of(Event.class));
        eventManager.setTriggerManager(triggerManager);
        eventManager.setEventMatcher(eventMatcher);
    }

    @Test
    void triggerDispatchesEventToMatchingHandlers() {
        SubscriptionSpec<Event> spec = mock(SubscriptionSpec.class);
        Event event = mock(Event.class);
        when(spec.getEventType()).thenReturn(Event.class);
        when(eventMatcher.matches(spec, event)).thenReturn(true);

        eventManager.subscribe(spec, handler);
        eventManager.dispatch(event).join();

        verify(handler).handle(eq(event), any());
    }

    @Test
    void triggerDoesNotDispatchEventToNonMatchingHandlers() {
        SubscriptionSpec<Event> spec = mock(SubscriptionSpec.class);
        Event event = mock(Event.class);
        when(spec.getEventType()).thenReturn(Event.class);
        when(eventMatcher.matches(spec, event)).thenReturn(false);

        eventManager.subscribe(spec, handler);
        eventManager.dispatch(event).join();

        verifyNoInteractions(handler);
    }

    @Test
    void handleEventCapturesExceptions() {
        SubscriptionSpec<Event> spec = mock(SubscriptionSpec.class);
        Event event = mock(Event.class);
        when(spec.getEventType()).thenReturn(Event.class);
        when(eventMatcher.matches(spec, event)).thenReturn(true);
        doThrow(new RuntimeException("Handler error")).when(handler).handle(any(), any());

        Subscription subscription = eventManager.subscribe(spec, handler);
        eventManager.dispatch(event).join();

        assertNotNull(subscription.getErrors());
        assertEquals(1, subscription.getErrors().size());
        assertEquals("Handler error", subscription.getErrors().get(0).getMessage());
    }

    @Test
    void subscribeReturnsActiveSubscription() {
        SubscriptionSpec<Event> spec = mock(SubscriptionSpec.class);
        EventHandler<Event> handler = mock(EventHandler.class);
        when(spec.getEventType()).thenReturn(Event.class);

        Subscription subscription = eventManager.subscribe(spec, handler);

        assertNotNull(subscription);
        assertTrue(subscription.isActive());
        verify(triggerManager).enable(spec, eventManager);
    }

    @Test
    void subscribeReturnsInactiveSubscriptionForNonMatchingSpec() {
        SubscriptionSpec<SomeOtherEvent> spec = mock(SubscriptionSpec.class);
        EventHandler<SomeOtherEvent> handler = mock(EventHandler.class);
        when(spec.getEventType()).thenReturn(SomeOtherEvent.class);

        Subscription subscription = eventManager.subscribe(spec, handler);

        assertNotNull(subscription);
        assertFalse(subscription.isActive());
        verify(triggerManager, never()).enable(any(), any());
    }

    @Test
    void unsubscribeRemovesHandlerAndDisablesTrigger() {
        SubscriptionSpec<Event> spec = mock(SubscriptionSpec.class);
        EventHandler<Event> handler = mock(EventHandler.class);
        when(spec.getEventType()).thenReturn(Event.class);

        Subscription subscription = eventManager.subscribe(spec, handler);
        subscription.unsubscribe();
        eventManager.dispatch(new SomeOtherEvent(UUID.randomUUID())).join();

        assertFalse(subscription.isActive());
        verify(triggerManager).disable(spec);
        verifyNoInteractions(handler);
    }
}
