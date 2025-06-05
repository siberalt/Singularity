package com.siberalt.singularity.broker.impl.mock;

import com.siberalt.singularity.broker.contract.service.event.dispatcher.events.NewCandleEvent;
import com.siberalt.singularity.broker.contract.service.event.dispatcher.subscriptions.NewCandleSubscriptionSpec;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.CandleRepository;
import com.siberalt.singularity.event.Event;
import com.siberalt.singularity.event.EventHandler;
import com.siberalt.singularity.event.subscription.DefaultSubscription;
import com.siberalt.singularity.event.subscription.Subscription;
import com.siberalt.singularity.event.subscription.SubscriptionManager;
import com.siberalt.singularity.event.subscription.SubscriptionSpec;
import com.siberalt.singularity.simulation.EventInvoker;
import com.siberalt.singularity.simulation.EventObserver;
import com.siberalt.singularity.simulation.Initializable;
import com.siberalt.singularity.simulation.TimeDependentUnit;
import com.siberalt.singularity.strategy.context.Clock;

import java.time.Instant;
import java.util.*;

public class NewCandleSubscriptionManager implements SubscriptionManager, EventInvoker, Initializable, TimeDependentUnit {
    private final CandleRepository candleRepository;
    private HashMap<String, Iterator<Candle>> candleIterator;
    private final Set<String> instrumentIds;
    private EventObserver eventObserver;
    private Clock clock;
    private final HashMap<Instant, List<Candle>> eventCandles = new HashMap<>();
    private final HashMap<SubscriptionSpec<?>, List<EventHandler<?>>> eventHandlers = new HashMap<>();
    private final HashMap<EventHandler<?>, DefaultSubscription> handlerSubscriptions = new HashMap<>();

    public NewCandleSubscriptionManager(CandleRepository candleRepository, Set<String> instrumentIds) {
        this.candleRepository = candleRepository;
        this.instrumentIds = instrumentIds;
    }

    @Override
    public <T extends Event> Subscription subscribe(SubscriptionSpec<T> spec, EventHandler<T> handler) {
        boolean active = (spec instanceof NewCandleSubscriptionSpec newCandleSubscription)
            && instrumentIds.containsAll(newCandleSubscription.getInstrumentIds())
            && newCandleSubscription.getEventType().equals(NewCandleEvent.class);

        if (!active) {
            return new DefaultSubscription(false, () -> {});
        }

        List<EventHandler<?>> handlers = eventHandlers.computeIfAbsent(
            spec, subscriptionSpec -> new ArrayList<>()
        );
        handlers.add(handler);

        DefaultSubscription existingSubscription = new DefaultSubscription(true, () -> {});
        handlerSubscriptions.put(handler, existingSubscription);

        return existingSubscription;
    }

    @Override
    public void observeEventsBy(EventObserver observer) {
        this.eventObserver = observer;
    }

    @Override
    public void init(Instant startTime, Instant endTime) {
        candleIterator = new HashMap<>();
        for (String instrumentId : instrumentIds) {
            Iterable<Candle> candles = candleRepository.getPeriod(instrumentId, startTime, endTime);
            Iterator<Candle> iterator = candles.iterator();
            candleIterator.put(instrumentId, iterator);

            if (iterator.hasNext()) {
                scheduleCandleEvent(iterator.next());
            }
        }
    }

    @Override
    public void applyClock(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void tick() {
        Instant currentTime = clock.currentTime();

        if (eventCandles.containsKey(currentTime)) {
            for (Candle eventCandle : eventCandles.get(currentTime)) {
                NewCandleEvent newCandleEvent = new NewCandleEvent(eventCandle);
                // Notify all event handlers for the new candle
                for (Map.Entry<SubscriptionSpec<?>, List<EventHandler<?>>> entry : eventHandlers.entrySet()) {
                    if (matches(entry.getKey(), newCandleEvent)) {
                        for (EventHandler<?> handler : entry.getValue()) {
                            @SuppressWarnings("unchecked")
                            EventHandler<NewCandleEvent> specificHandler = (EventHandler<NewCandleEvent>) handler;
                            DefaultSubscription subscription = handlerSubscriptions.get(handler);

                            if (!subscription.isActive()) {
                                // If the subscription is inactive, skip handling
                                continue;
                            }

                            try {
                                specificHandler.handle(newCandleEvent, subscription);
                            } catch (Throwable throwable) {
                                subscription.setErrors(List.of(throwable));
                            }
                        }
                    }
                }

                Iterator<Candle> iterator = candleIterator.get(eventCandle.getInstrumentUid());

                if (iterator != null && iterator.hasNext()) {
                    scheduleCandleEvent(iterator.next());
                } else {
                    // If no more candles are available, we can stop scheduling events for this instrument
                    candleIterator.remove(eventCandle.getInstrumentUid());
                }
            }

            // Clear the processed candles for the current time
            eventCandles.remove(currentTime);

            // Clear inactive subscriptions from the event handlers
            clearInactiveSubscriptions();
        }
    }

    private void clearInactiveSubscriptions() {
        eventHandlers.values()
            .forEach(handlers -> handlers.removeIf(handler -> !handlerSubscriptions.get(handler).isActive()));
        eventHandlers.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    private boolean matches(SubscriptionSpec<?> subscription, Event event) {
        // Check if the event type matches the subscription
        if (!subscription.getEventType().isAssignableFrom(event.getClass())) {
            return false;
        }
        // Check if the event matches the subscription criteria
        return event instanceof NewCandleEvent newCandleEvent &&
            ((NewCandleSubscriptionSpec) subscription).getInstrumentIds()
                .contains(newCandleEvent.getCandle().getInstrumentUid());
    }

    private void scheduleCandleEvent(Candle candle) {
        // Schedule the next candle event
        eventCandles
            .computeIfAbsent(candle.getTime(), k -> new ArrayList<>())
            .add(candle);

        eventObserver.scheduleEvent(
            com.siberalt.singularity.simulation.Event.create(candle.getTime(), this)
        );
    }
}
