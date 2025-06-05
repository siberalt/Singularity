package com.siberalt.singularity.broker.impl.mock;

import com.siberalt.singularity.broker.contract.service.event.dispatcher.events.NewCandleEvent;
import com.siberalt.singularity.broker.contract.service.event.dispatcher.subscriptions.NewCandleSubscriptionSpec;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.CandleRepository;
import com.siberalt.singularity.event.EventHandler;
import com.siberalt.singularity.event.subscription.Subscription;
import com.siberalt.singularity.simulation.EventSimulator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class NewCandleSubscriptionManagerTest {
    @Mock
    private CandleRepository candleRepository;

    private EventSimulator eventSimulator;

    private NewCandleSubscriptionManager subscriptionManager;

    @BeforeEach
    public void setUp() {
        subscriptionManager = new NewCandleSubscriptionManager(candleRepository, Set.of("instrument1", "instrument2"));

        eventSimulator = new EventSimulator();
        eventSimulator.addEventInvoker(subscriptionManager);
        eventSimulator.addInitializableUnit(subscriptionManager);
        eventSimulator.addTimeDependentUnit(subscriptionManager);
    }

    @Test
    public void subscribeReturnsActiveSubscriptionForValidSpec() {
        NewCandleSubscriptionSpec validSpec = new NewCandleSubscriptionSpec(Set.of("instrument1"));
        EventHandler<NewCandleEvent> handler = (event, subscription) -> {
        };

        Subscription subscription = subscriptionManager.subscribe(validSpec, handler);

        assertTrue(subscription.isActive());
    }

    @Test
    public void subscribeReturnsInactiveSubscriptionForInvalidSpec() {
        NewCandleSubscriptionSpec invalidSubscription = new NewCandleSubscriptionSpec(Set.of("invalidInstrument"));
        EventHandler<NewCandleEvent> handler = (event, subscription) -> {
        };

        Subscription subscription = subscriptionManager.subscribe(invalidSubscription, handler);

        assertFalse(subscription.isActive());
    }

    @Test
    public void tickNotifiesHandlersForScheduledEvents() {
        Instant candleTime = Instant.parse("2020-12-30T07:00:00Z");
        Candle candle1 = Candle.of(candleTime, "instrument1", 1000, 12);
        Candle candle2 = Candle.of(candleTime, "instrument2", 2000, 24);

        // Stubbing for both "instrument1" and "instrument2"
        when(candleRepository.getPeriod(eq("instrument1"), any(), any())).thenReturn(Set.of(candle1));
        when(candleRepository.getPeriod(eq("instrument2"), any(), any())).thenReturn(Set.of(candle2));

        NewCandleEvent event1 = new NewCandleEvent(candle1);
        EventHandler<NewCandleEvent> handler = mock(EventHandler.class);
        NewCandleSubscriptionSpec spec = new NewCandleSubscriptionSpec(Set.of("instrument1"));

        Subscription subscription = subscriptionManager.subscribe(spec, handler);
        eventSimulator.run(candleTime.minusSeconds(10), candleTime.plusSeconds(10));

        verify(handler).handle(event1, subscription);
        verifyNoMoreInteractions(handler);
    }

    @Test
    void tickDoesNotNotifyHandlersForUnscheduledEvents() {
        Instant time = Instant.parse("2020-12-30T07:00:00Z");

        when(candleRepository.getPeriod(eq("instrument1"), any(), any())).thenReturn(Collections.emptySet());
        when(candleRepository.getPeriod(eq("instrument2"), any(), any())).thenReturn(Collections.emptySet());

        EventHandler<NewCandleEvent> handler = mock(EventHandler.class);
        NewCandleSubscriptionSpec subscription = new NewCandleSubscriptionSpec(Set.of("instrument1"));

        subscriptionManager.subscribe(subscription, handler);
        eventSimulator.run(time.minusSeconds(10), time.plusSeconds(10));

        verifyNoInteractions(handler);
    }

    @Test
    void tickMultipleCandlesForEachInstrument() {
        Instant candleTime1 = Instant.parse("2020-12-30T07:00:00Z");
        Instant candleTime2 = Instant.parse("2020-12-30T07:01:00Z");
        Instant candleTime3 = Instant.parse("2020-12-30T08:00:00Z");
        Instant candleTime4 = Instant.parse("2020-12-30T08:01:00Z");

        Candle candle1 = Candle.of(candleTime1, "instrument1", 1000, 12);
        Candle candle2 = Candle.of(candleTime2, "instrument1", 1100, 13);
        Candle candle3 = Candle.of(candleTime3, "instrument2", 2000, 24);
        Candle candle4 = Candle.of(candleTime4, "instrument2", 2100, 25);

        when(candleRepository.getPeriod(eq("instrument1"), any(), any())).thenReturn(Set.of(candle1, candle2));
        when(candleRepository.getPeriod(eq("instrument2"), any(), any())).thenReturn(Set.of(candle3, candle4));

        NewCandleEvent event1 = new NewCandleEvent(candle1);
        NewCandleEvent event2 = new NewCandleEvent(candle2);
        NewCandleEvent event3 = new NewCandleEvent(candle3);
        NewCandleEvent event4 = new NewCandleEvent(candle4);

        EventHandler<NewCandleEvent> handler = mock(EventHandler.class);
        NewCandleSubscriptionSpec subscriptionSpec = new NewCandleSubscriptionSpec(Set.of("instrument1", "instrument2"));

        Subscription subscription = subscriptionManager.subscribe(subscriptionSpec, handler);
        eventSimulator.run(candleTime1.minusSeconds(10), candleTime4.plusSeconds(10));

        verify(handler).handle(event1, subscription);
        verify(handler).handle(event2, subscription);
        verify(handler).handle(event3, subscription);
        verify(handler).handle(event4, subscription);
        verifyNoMoreInteractions(handler);
    }

    @Test
    void unsubscribeRemovesHandler() {
        NewCandleSubscriptionSpec validSpec = new NewCandleSubscriptionSpec(Set.of("instrument1"));
        Instant candleTime = Instant.parse("2020-12-30T07:00:00Z");
        Candle candle = Candle.of(candleTime, "instrument1", 1000, 12);

        EventHandler<NewCandleEvent> handler = mock(EventHandler.class);
        when(candleRepository.getPeriod(eq("instrument1"), any(), any())).thenReturn(Set.of(candle));
        when(candleRepository.getPeriod(eq("instrument2"), any(), any())).thenReturn(Collections.emptySet());

        Subscription subscription = subscriptionManager.subscribe(
            validSpec,
            (event, subscription1) -> subscription1.unsubscribe()
        );

        assertTrue(subscription.isActive());

        // Verify that the handler is no longer called
        eventSimulator.run(
            candleTime.minus(3, ChronoUnit.SECONDS),
            candleTime.plus(3, ChronoUnit.SECONDS)
        );
        verifyNoInteractions(handler);
        assertFalse(subscription.isActive());
    }

    @Test
    void throwExceptionOnEventHandler() {
        NewCandleSubscriptionSpec validSpec = new NewCandleSubscriptionSpec(Set.of("instrument1"));
        Instant candleTime = Instant.parse("2020-12-30T07:00:00Z");
        Candle candle = Candle.of(candleTime, "instrument1", 1000, 12);

        EventHandler<NewCandleEvent> handler = (event, subscription) -> {
            throw new RuntimeException("Test exception");
        };

        when(candleRepository.getPeriod(eq("instrument1"), any(), any())).thenReturn(Set.of(candle));
        when(candleRepository.getPeriod(eq("instrument2"), any(), any())).thenReturn(Collections.emptySet());

        Subscription subscription = subscriptionManager.subscribe(validSpec, handler);

        assertTrue(subscription.isActive());

        // Run the event simulator
        eventSimulator.run(
            candleTime.minus(3, ChronoUnit.SECONDS),
            candleTime.plus(3, ChronoUnit.SECONDS)
        );

        // Verify that the subscription is still active despite the exception
        assertTrue(subscription.isActive());
        assertFalse(subscription.getErrors().isEmpty());
    }
}
