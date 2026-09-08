package com.siberalt.singularity.broker.impl.mock;

import com.siberalt.singularity.broker.contract.service.event.dispatcher.events.NewCandleEvent;
import com.siberalt.singularity.broker.contract.service.event.dispatcher.subscriptions.NewCandleSubscriptionSpec;
import com.siberalt.singularity.broker.shared.CandleEventMatcher;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.ReadCandleRepository;
import com.siberalt.singularity.event.Event;
import com.siberalt.singularity.event.EventHandler;
import com.siberalt.singularity.event.EventManager;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

/**
 * Replays an instrument's recorded candles as if they were arriving live, one at a time as the
 * simulated clock reaches each of them.
 * <p>
 * That replay is the whole of what is specific to a simulation. Keeping track of who is subscribed
 * to what, matching an event against the specs, handing it to the right handlers and letting a
 * handler unsubscribe itself are the same problems a live broker has, and {@link EventManager}
 * already solves them - so they are delegated to it rather than done again here, the same way
 * {@code TinkoffCandleSubscriptionManager} does.
 * <p>
 * The one thing that differs is which thread the handlers run on. A live feed dispatches on its own
 * executor; a simulation must not, because a strategy reacting to a candle has to have finished
 * reacting before the clock moves on. So the event manager is given an executor that runs the work
 * on the caller's thread, which keeps a run ordered and repeatable.
 */
public class NewCandleSubscriptionManager implements SubscriptionManager, EventInvoker, Initializable, TimeDependentUnit {
    private static final Logger logger = LoggerFactory.getLogger(NewCandleSubscriptionManager.class);

    private final ReadCandleRepository candleRepository;
    private final Supplier<Set<String>> instrumentIdsSupplier;
    private final EventManager eventManager;
    private final Map<Instant, List<Candle>> candlesByTime = new HashMap<>();
    private Map<String, Iterator<Candle>> candlesByInstrument;
    private Set<String> instrumentIds;
    private EventObserver eventObserver;
    private Clock clock;
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
        // Runnable::run - handlers run on the simulation's own thread, in order, before the clock
        // is allowed to move.
        this.eventManager = new EventManager(Runnable::run, Set.of(NewCandleEvent.class));
        this.eventManager.setEventMatcher(new CandleEventMatcher());
    }

    public boolean isInterruptOnError() {
        return interruptOnError;
    }

    /**
     * Whether a handler throwing takes the run down with it. On by default: in a backtest a broken
     * strategy usually means the numbers that follow are meaningless, and finding out at the end is
     * worse than stopping. Switch it off to let the run continue - the error is kept on the
     * subscription either way.
     */
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

        return eventManager.subscribe(spec, handler);
    }

    @Override
    public void observeEventsBy(EventObserver observer) {
        this.eventObserver = observer;
    }

    @Override
    public void init(Instant startTime, Instant endTime) {
        instrumentIds = instrumentIdsSupplier.get();
        candlesByInstrument = new HashMap<>();

        for (String instrumentId : instrumentIds) {
            Iterator<Candle> candles = candleRepository.getPeriod(instrumentId, startTime, endTime).iterator();
            candlesByInstrument.put(instrumentId, candles);
            scheduleNext(instrumentId);
        }
    }

    @Override
    public void applyClock(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void tick() {
        List<Candle> dueCandles = candlesByTime.remove(clock.currentTime());

        if (dueCandles == null) {
            return;
        }

        for (Candle candle : dueCandles) {
            deliver(candle);
            scheduleNext(candle.instrumentUid());
        }
    }

    /**
     * Hands the candle to whoever is subscribed to its instrument. The handlers have all run by the
     * time the dispatch returns - that is what the caller-thread executor buys - so the failure of
     * any of them is known here rather than somewhere later.
     */
    protected void deliver(Candle candle) {
        try {
            eventManager.dispatch(new NewCandleEvent(candle)).join();
        } catch (CompletionException dispatchFailed) {
            Throwable cause = dispatchFailed.getCause() != null ? dispatchFailed.getCause() : dispatchFailed;
            logger.error("Error occurred while handling event", cause);

            if (!interruptOnError) {
                return;
            }

            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }

            if (cause instanceof Error error) {
                throw error;
            }

            throw dispatchFailed;
        }
    }

    /**
     * Books the instrument's next recorded candle as a simulation event. An instrument whose
     * candles have run out simply stops being scheduled.
     */
    protected void scheduleNext(String instrumentUid) {
        Iterator<Candle> candles = candlesByInstrument.get(instrumentUid);

        if (candles == null || !candles.hasNext()) {
            candlesByInstrument.remove(instrumentUid);

            return;
        }

        Candle candle = candles.next();
        candlesByTime.computeIfAbsent(candle.getTime(), moment -> new ArrayList<>()).add(candle);
        eventObserver.scheduleEvent(
            com.siberalt.singularity.simulation.Event.create(candle.getTime(), this)
        );
    }

    private Set<String> getInstrumentIds() {
        if (instrumentIds == null) {
            instrumentIds = instrumentIdsSupplier.get();
        }

        return instrumentIds;
    }
}
