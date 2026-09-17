package com.siberalt.singularity.broker.impl.mock;

import com.siberalt.singularity.broker.contract.service.event.dispatcher.events.NewCandleEvent;
import com.siberalt.singularity.broker.contract.service.event.dispatcher.subscriptions.NewCandleSubscriptionSpec;
import com.siberalt.singularity.broker.shared.CandleEventMatcher;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.ReadCandleRepository;
import com.siberalt.singularity.entity.instrument.InstrumentIdResolver;
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

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
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
 * <p>
 * Subscriptions name instruments as the broker does, by uid, and recorded candles are kept by our
 * instrument id, so each uid is translated once when the replay starts. An event carries both: the
 * uid it was subscribed for, and the candle with its id.
 */
public class NewCandleSubscriptionManager implements SubscriptionManager, EventInvoker, Initializable, TimeDependentUnit {
    private static final Logger logger = LoggerFactory.getLogger(NewCandleSubscriptionManager.class);

    private record Scheduled(String instrumentUid, Candle candle) {
    }

    private final ReadCandleRepository candleRepository;
    private final InstrumentIdResolver instrumentIdResolver;
    private final Supplier<Set<String>> instrumentIdsSupplier;
    private final EventManager eventManager;
    private final Map<Instant, List<Scheduled>> candlesByTime = new HashMap<>();
    private Map<String, Iterator<Candle>> candlesByInstrument;
    private Set<String> instrumentIds;
    private EventObserver eventObserver;
    private Clock clock;
    private boolean interruptOnError = true;
    private Duration replayChunk = Duration.ofDays(7);

    public NewCandleSubscriptionManager(
        ReadCandleRepository candleRepository,
        InstrumentIdResolver instrumentIdResolver,
        Set<String> instrumentIds
    ) {
        this(candleRepository, instrumentIdResolver, () -> instrumentIds);
    }

    /**
     * Resolves the instruments to simulate lazily rather than taking a snapshot at construction
     * time: the set is fixed at {@link #init}, together with the candle iterators built from it, so
     * instruments registered between constructing the broker and starting the simulation are seen.
     */
    public NewCandleSubscriptionManager(
        ReadCandleRepository candleRepository,
        InstrumentIdResolver instrumentIdResolver,
        Supplier<Set<String>> instrumentIdsSupplier
    ) {
        this.candleRepository = candleRepository;
        this.instrumentIdResolver = instrumentIdResolver;
        this.instrumentIdsSupplier = instrumentIdsSupplier;
        // Runnable::run - handlers run on the simulation's own thread, in order, before the clock
        // is allowed to move.
        this.eventManager = new EventManager(Runnable::run, Set.of(NewCandleEvent.class));
        this.eventManager.setEventMatcher(new CandleEventMatcher());
    }

    public Duration getReplayChunk() {
        return replayChunk;
    }

    /**
     * How much of the history is read from the repository at a time. A week by default.
     * <p>
     * The replay used to read the whole simulated period up front and walk it, which holds every
     * candle of the run in memory until the run ends - about a hundred and fifty megabytes for two
     * years of minutes, and that times however many runs share a process. Read a chunk at a time,
     * a run holds a week of candles whatever its length.
     * <p>
     * Reading lazily means the repository is asked while the simulation runs rather than before it,
     * so it must not itself be limited to the simulated present: a chunk cut short at the clock
     * would be taken as all there is up to the chunk's end, and the rest of it never replayed.
     */
    public NewCandleSubscriptionManager setReplayChunk(Duration replayChunk) {
        if (replayChunk == null || replayChunk.isZero() || replayChunk.isNegative()) {
            throw new IllegalArgumentException("A replay chunk has to span some time, got " + replayChunk);
        }

        this.replayChunk = replayChunk;
        return this;
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

        for (String instrumentUid : instrumentIds) {
            // An instrument the broker trades but whose candles cannot be found is a wiring error, not a
            // quiet market: replaying nothing for it would look exactly like a strategy that never traded.
            long instrumentId = instrumentIdResolver.idOf(instrumentUid).orElseThrow(() -> new IllegalStateException(
                "No candle history id is known for instrument " + instrumentUid
                    + ": the resolver this broker was given does not map it"));
            candlesByInstrument.put(instrumentUid, new ChunkedCandles(instrumentId, startTime, endTime));
            scheduleNext(instrumentUid);
        }
    }

    @Override
    public void applyClock(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void tick() {
        List<Scheduled> dueCandles = candlesByTime.remove(clock.currentTime());

        if (dueCandles == null) {
            return;
        }

        for (Scheduled due : dueCandles) {
            deliver(due.instrumentUid(), due.candle());
            scheduleNext(due.instrumentUid());
        }
    }

    /**
     * Hands the candle to whoever is subscribed to its instrument. The handlers have all run by the
     * time the dispatch returns - that is what the caller-thread executor buys - so the failure of
     * any of them is known here rather than somewhere later.
     */
    protected void deliver(String instrumentUid, Candle candle) {
        try {
            eventManager.dispatch(new NewCandleEvent(instrumentUid, candle)).join();
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
        candlesByTime.computeIfAbsent(candle.getTime(), moment -> new ArrayList<>()).add(new Scheduled(instrumentUid, candle));
        eventObserver.scheduleEvent(
            com.siberalt.singularity.simulation.Event.create(candle.getTime(), this)
        );
    }

    /**
     * One instrument's candles between two instants, oldest first, read from the repository a chunk
     * at a time as the replay reaches them.
     * <p>
     * Consecutive chunks share their boundary instant, and whatever the boundary returns twice is
     * dropped by time. That keeps the replay whole under either reading of a period's end: a
     * repository counting the end in returns the boundary candle twice, one leaving it out returns
     * it once, from the next chunk. The last chunk ends exactly where the whole period did, so what
     * the replay covers is what a single read of the period would have.
     */
    private class ChunkedCandles implements Iterator<Candle> {
        private final long instrumentId;
        private final Instant end;
        private Instant cursor;
        private Iterator<Candle> chunk = Collections.emptyIterator();
        private boolean readToEnd;
        private Candle next;
        private Instant lastTime;

        ChunkedCandles(long instrumentId, Instant start, Instant end) {
            this.instrumentId = instrumentId;
            this.cursor = start;
            this.end = end;
            advance();
        }

        @Override
        public boolean hasNext() {
            return next != null;
        }

        @Override
        public Candle next() {
            if (next == null) {
                throw new NoSuchElementException("The replay of instrument " + instrumentId + " has ended");
            }

            Candle candle = next;
            advance();

            return candle;
        }

        private void advance() {
            next = null;

            while (true) {
                while (chunk.hasNext()) {
                    Candle candle = chunk.next();

                    if (lastTime == null || candle.getTime().isAfter(lastTime)) {
                        lastTime = candle.getTime();
                        next = candle;

                        return;
                    }
                }

                if (readToEnd) {
                    // Let go of the last chunk's list along with the replay.
                    chunk = Collections.emptyIterator();

                    return;
                }

                Instant chunkEnd = cursor.plus(replayChunk);

                if (!chunkEnd.isBefore(end)) {
                    chunkEnd = end;
                    readToEnd = true;
                }

                chunk = candleRepository.getPeriod(instrumentId, cursor, chunkEnd).iterator();
                cursor = chunkEnd;
            }
        }
    }

    private Set<String> getInstrumentIds() {
        if (instrumentIds == null) {
            instrumentIds = instrumentIdsSupplier.get();
        }

        return instrumentIds;
    }
}
