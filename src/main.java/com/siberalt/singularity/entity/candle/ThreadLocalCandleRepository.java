package com.siberalt.singularity.entity.candle;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Gives every thread its own repository, so a source that cannot be shared can still be read from
 * several threads at once.
 * <p>
 * The one that cannot be shared here is a database connection, and it is what a candle repository
 * usually is. {@link CachingCandleRepository} deliberately calls its delegate without holding a
 * lock - holding one across the read turned a parallel walk-forward into a queue - so the delegate
 * it wraps has to stand concurrent calls, and this is what makes an ordinary one do so.
 * <p>
 * The pair composes in one order only: the cache on the outside, this underneath. Wrapped the other
 * way round each thread would keep its own cache and decode the same history again per thread,
 * which is most of what the cache was for.
 * <p>
 * Nothing is ever released. The repositories live as long as the threads that asked for them, which
 * is right for a pool that lasts a run and wrong for threads created and discarded in a loop.
 */
public class ThreadLocalCandleRepository implements ReadCandleRepository {
    private final ThreadLocal<ReadCandleRepository> perThread;

    /**
     * @param factory builds one repository, called once per thread that reads. It has to hand back
     *                a new instance each time - returning the same one would put every thread back
     *                on the source this class exists to keep them off.
     */
    public ThreadLocalCandleRepository(Supplier<ReadCandleRepository> factory) {
        if (factory == null) {
            throw new IllegalArgumentException("Nothing to build repositories with");
        }

        this.perThread = ThreadLocal.withInitial(factory);
    }

    protected ReadCandleRepository repository() {
        return perThread.get();
    }

    @Override
    public Optional<Candle> getAt(String instrumentUid, Instant at) {
        return repository().getAt(instrumentUid, at);
    }

    @Override
    public List<Candle> findBeforeOrEqual(String instrumentUid, Instant at, long amountBefore) {
        return repository().findBeforeOrEqual(instrumentUid, at, amountBefore);
    }

    @Override
    public List<Candle> findAfterOrEqual(String instrumentUid, Instant at, long amountAfter) {
        return repository().findAfterOrEqual(instrumentUid, at, amountAfter);
    }

    @Override
    public List<Candle> findByPrice(FindPriceParams params) {
        return repository().findByPrice(params);
    }

    @Override
    public List<Candle> getPeriod(String instrumentUid, Instant from, Instant to) {
        return repository().getPeriod(instrumentUid, from, to);
    }

    @Override
    public CandleRangeMetadata getRangeMetadata(String instrumentUid, Instant from, Instant to) {
        return repository().getRangeMetadata(instrumentUid, from, to);
    }
}
