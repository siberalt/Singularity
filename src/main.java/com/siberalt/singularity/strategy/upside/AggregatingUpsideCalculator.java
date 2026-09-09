package com.siberalt.singularity.strategy.upside;

import com.siberalt.singularity.broker.contract.service.market.request.CandleInterval;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.CandleAggregator;

import java.util.ArrayList;
import java.util.List;

/**
 * Rolls the candles up to a wider interval and passes on only the bars that have closed, so a
 * calculator written for one timeframe can be run on another without either it or the strategy
 * knowing.
 * <p>
 * Between one wide bar and the next it answers {@link Upside#NEUTRAL}, and that is the point rather
 * than a gap: a strategy fed minute candles asks for a decision every minute, and a signal read
 * from hourly bars has nothing new to say until the hour is out. Answering with the previous hour's
 * verdict would have the strategy act on it sixty times and pay sixty round trips for one decision.
 * <p>
 * Only closed bars are passed on. The hour in progress is held back until a candle from the next
 * one arrives, so the wide bar the signal reads is one whose high, low, close and volume are all
 * settled - a bar still forming would have the calculator reading a partial volume as a real one.
 */
public class AggregatingUpsideCalculator implements UpsideCalculator {
    private final CandleInterval interval;
    private final UpsideCalculator delegate;
    private final CandleAggregator aggregator = new CandleAggregator();
    private final List<Candle> openBar = new ArrayList<>();
    private long openBucket = Long.MIN_VALUE;

    /**
     * @param interval the width to roll up to
     * @param delegate the calculator underneath, which sees one candle per closed interval and can
     *                 keep its own window over them - a {@link WindowUpsideCalculator} placed here
     *                 holds that many wide bars, not that many of the original ones
     */
    public AggregatingUpsideCalculator(CandleInterval interval, UpsideCalculator delegate) {
        this.interval = interval;
        this.delegate = delegate;
    }

    @Override
    public Upside calculate(List<Candle> lastCandles) {
        Upside upside = Upside.NEUTRAL;

        for (Candle candle : lastCandles) {
            long bucket = aggregator.bucketOf(candle, interval);

            if (bucket != openBucket) {
                if (!openBar.isEmpty()) {
                    upside = delegate.calculate(List.of(aggregator.merge(openBar)));
                }

                openBar.clear();
                openBucket = bucket;
            }

            openBar.add(candle);
        }

        return upside;
    }
}
