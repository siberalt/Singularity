package com.siberalt.singularity.broker.impl.mock;

import com.siberalt.singularity.broker.contract.service.event.dispatcher.events.NewCandleEvent;
import com.siberalt.singularity.broker.contract.service.event.dispatcher.subscriptions.NewCandleSubscriptionSpec;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.ReadCandleRepository;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;

public class NewCandleSubscriptionManager implements SubscriptionManager, EventInvoker, Initializable, TimeDependentUnit {
    private static final Logger logger = LoggerFactory.getLogger(NewCandleSubscriptionManager.class);
    private final ReadCandleRepository candleRepository;
    private HashMap<String, Iterator<Candle>> candleIterator;
    private final Supplier<Set<String>> instrumentIdsSupplier;
    private Set<String> instrumentIds;
    private EventObserver eventObserver;
    private Clock clock;
    private final HashMap<Instant, List<Candle>> eventCandles = new HashMap<>();
    private final HashMap<SubscriptionSpec<?>, List<EventHandler<?>>> eventHandlers = new HashMap<>();
    private final HashMap<EventHandler<?>, DefaultSubscription> handlerSubscriptions = new HashMap<>();
    private boolean interruptOnError = true;

    public NewCandleSubscriptionManager(ReadCandleRepository candleRepository, Set<String> instrumentIds) {
        this(candleRepository, () -> instrumentIds);
    }

    /**
     * Resolves the instruments to simulate lazily rather than taking a snapshot at construction
     * time: the set is fixed at {@link #init}, together with the candle iterators built from it, so
     * instruments registered between constructing the broker and starting the simulation are seen.
     */
    public NewCandleSubscriptionManager(
        ReadCandleRepository candleRepository,
        Supplier<Set<String>> instrumentIdsSupplier
    ) {
        this.candleRepository = candleRepository;
        this.instrumentIdsSupplier = instrumentIdsSupplier;
    }

    public boolean isInterruptOnError() {
        return interruptOnError;
    }

    public NewCandleSubscriptionManager setInterruptOnError(boolean interruptOnError) {
        this.interruptOnError = interruptOnError;
        return this;
    }

    /**
     * A spec this manager cannot serve is a wiring error, not a subscription that happens to stay
     * quiet: handing back an inactive subscription used to make it indistinguishable from a
     * strategy that simply never traded, so it is rejected outright.
     */
    @Override
    public <T extends Event> Subscription subscribe(SubscriptionSpec<T> spec, EventHandler<T> handler) {
        if (!(spec instanceof NewCandleSubscriptionSpec newCandleSubscription)) {
            throw new IllegalArgumentException(
                String.format(
                    "%s only serves %s, got %s for event type %s",
                    getClass().getSimpleName(),
                    NewCandleSubscriptionSpec.class.getSimpleName(),
                    spec.getClass().getSimpleName(),
                    spec.getEventType().getSimpleName()
                )
            );
        }

        Set<String> knownInstrumentIds = getInstrumentIds();

        if (!knownInstrumentIds.containsAll(newCandleSubscription.getInstrumentIds())) {
            Set<String> unknownInstrumentIds = new HashSet<>(newCandleSubscription.getInstrumentIds());
            unknownInstrumentIds.removeAll(knownInstrumentIds);

            throw new IllegalArgumentException(
                String.format(
                    "No candles are simulated for instruments %s; this broker only knows %s",
                    unknownInstrumentIds,
                    knownInstrumentIds
                )
            );
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
        instrumentIds = instrumentIdsSupplier.get();
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
                                logger.error("Error occurred while handling event", throwable);
                                subscription.addError(throwable);

                                if (interruptOnError) {
                                    throw throwable;
                                }
                            }
                        }
                    }
                }

                Iterator<Candle> iterator = candleIterator.get(eventCandle.instrumentUid());

                if (iterator != null && iterator.hasNext()) {
                    scheduleCandleEvent(iterator.next());
                } else {
                    // If no more candles are available, we can stop scheduling events for this instrument
                    candleIterator.remove(eventCandle.instrumentUid());
                }
            }

            // Clear the processed candles for the current time
            eventCandles.remove(currentTime);

            // Clear inactive subscriptions from the event handlers
            clearInactiveSubscriptions();
        }
    }

    private Set<String> getInstrumentIds() {
        if (instrumentIds == null) {
            instrumentIds = instrumentIdsSupplier.get();
        }

        return instrumentIds;
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
                .contains(newCandleEvent.getCandle().instrumentUid());
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
