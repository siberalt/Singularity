package com.siberalt.singularity.strategy.impl.quantity;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.ReadCandleRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Holds another sizing down to a share of what the instrument actually trades in a day - the size
 * asked for is {@code min(whatever the wrapped sizing wanted, share of average daily volume)}.
 * <p>
 * A decorator rather than a sizing of its own, because the cap is a separate concern from the
 * decision: whatever rule works out the size, this only ever says "not more than that". The
 * underlying rule keeps its say, including its right to ask for nothing.
 * <p>
 * The point of it is the mismatch a liquidity-aware simulation exposes. An order worth the whole
 * balance can be many hours of a thin instrument's volume, and it will neither fill promptly nor at
 * the price that prompted it. Sizing against the instrument instead of against the balance is what
 * makes the position reachable - and a strategy whose returns disappear once it is capped was
 * earning them from a fill it could not have got.
 */
public class AdvCappedQuantity implements TradeQuantity {
    /**
     * A twentieth of a day's volume. Small enough to be absorbed within a session at a sane
     * participation rate, and the usual starting point for anyone sizing against ADV.
     */
    public static final double DEFAULT_DAILY_VOLUME_SHARE = 0.05;

    /**
     * Roughly three weeks of one-minute bars on a Russian session, which runs a bit over five
     * hundred of them a day. Long enough that one busy day does not licence a large order, short
     * enough to follow an instrument whose activity is changing - but it is a count of bars, so
     * adjust it for any other bar size.
     */
    public static final long DEFAULT_LOOKBACK_CANDLES = 10_000;

    private final TradeQuantity delegate;
    private final ReadCandleRepository candleRepository;
    private double dailyVolumeShare = DEFAULT_DAILY_VOLUME_SHARE;
    private long lookbackCandles = DEFAULT_LOOKBACK_CANDLES;

    public AdvCappedQuantity(TradeQuantity delegate, ReadCandleRepository candleRepository) {
        this.delegate = delegate;
        this.candleRepository = candleRepository;
    }

    public double getDailyVolumeShare() {
        return dailyVolumeShare;
    }

    /**
     * The share of average daily volume a single order may ask for. Must be a share - past a whole
     * day's volume the cap stops capping anything.
     */
    public AdvCappedQuantity setDailyVolumeShare(double dailyVolumeShare) {
        if (dailyVolumeShare <= 0 || dailyVolumeShare > 1) {
            throw new IllegalArgumentException(
                String.format("Daily volume share must be within (0, 1], got %s", dailyVolumeShare)
            );
        }

        this.dailyVolumeShare = dailyVolumeShare;
        return this;
    }

    public long getLookbackCandles() {
        return lookbackCandles;
    }

    /**
     * How far back to look when averaging daily volume, counted in bars rather than in days.
     * <p>
     * A span of dates would sample a different amount of history depending on where the holidays
     * and half-days fall, so the cap would drift for reasons that have nothing to do with the
     * instrument. A count of bars always weighs the same amount of trading.
     */
    public AdvCappedQuantity setLookbackCandles(long lookbackCandles) {
        if (lookbackCandles <= 0) {
            throw new IllegalArgumentException("Lookback must be at least one candle, got " + lookbackCandles);
        }

        this.lookbackCandles = lookbackCandles;
        return this;
    }

    @Override
    public long toBuy(TradeMoment moment, long affordableLots) {
        return capped(moment, delegate.toBuy(moment, affordableLots));
    }

    @Override
    public long toSell(TradeMoment moment, long positionLots) {
        return capped(moment, delegate.toSell(moment, positionLots));
    }

    private long capped(TradeMoment moment, long wanted) {
        if (wanted <= 0) {
            return wanted;
        }

        long cap = dailyVolumeCap(moment);

        // No history to judge by is not a licence to trade any size, but neither is it a reason to
        // refuse - the underlying sizing is left to stand.
        return cap <= 0 ? wanted : Math.min(wanted, cap);
    }

    /**
     * A share of the instrument's average daily volume over the last {@link #getLookbackCandles()}
     * bars before this moment.
     * <p>
     * Both halves of the average dodge the calendar. The bars are taken by count, so the sample is
     * always the same amount of trading; and they are divided by the days those bars actually fall
     * on rather than by the days the span covers, so a holiday in the middle does not make the
     * instrument look quieter than it is.
     */
    protected long dailyVolumeCap(TradeMoment moment) {
        List<Candle> candles = candleRepository.findBeforeOrEqual(
            moment.instrumentId(),
            moment.at(),
            lookbackCandles
        );

        if (candles.isEmpty()) {
            return 0;
        }

        long totalVolume = 0;
        Set<Instant> tradingDays = new HashSet<>();

        for (Candle candle : candles) {
            totalVolume += candle.volume();
            tradingDays.add(candle.getTime().truncatedTo(ChronoUnit.DAYS));
        }

        return (long) (((double) totalVolume / Math.max(1, tradingDays.size())) * dailyVolumeShare);
    }
}
