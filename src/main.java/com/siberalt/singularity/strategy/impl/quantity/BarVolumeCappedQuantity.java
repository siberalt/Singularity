package com.siberalt.singularity.strategy.impl.quantity;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.ReadCandleRepository;

import java.util.List;

/**
 * Holds another sizing down to what a single bar can absorb, so an order finishes in the bar the
 * signal fired on instead of trailing across the next dozen.
 * <p>
 * This is the cap aimed at what actually costs a strategy money. The price of one fill is small -
 * spread and market impact together run a couple of basis points - but an order that takes eleven
 * bars to fill is still buying an hour after the reason to buy appeared, at prices the signal never
 * saw. Sized to one bar, the fill and the decision happen at the same moment, and the strategy gets
 * the trade it actually asked for.
 * <p>
 * The share is the strategy's own assumption about how much of a bar it could take, not the
 * broker's. It has to be: a simulation has a liquidity model to consult, and live trading has no
 * such number - a sizing that reached for one would only work in a backtest, which is precisely
 * backwards.
 *
 * @see AdvCappedQuantity for a ceiling on the position rather than on the fill - the two cap
 *      different things and compose
 */
public class BarVolumeCappedQuantity implements TradeQuantity {
    /**
     * A tenth of a bar. The conventional participation rate, and the same figure a simulation
     * typically assumes on the other side, so an order sized this way is one a bar can be expected
     * to take in full.
     */
    public static final double DEFAULT_BAR_VOLUME_SHARE = 0.1;

    /**
     * About a session of one-minute bars. Long enough that one quiet stretch does not shrink every
     * order, short enough to track an instrument whose activity is changing - and a count of bars
     * rather than a span of dates, so holidays do not move it.
     */
    public static final long DEFAULT_LOOKBACK_CANDLES = 500;

    private final TradeQuantity delegate;
    private final ReadCandleRepository candleRepository;
    private double barVolumeShare = DEFAULT_BAR_VOLUME_SHARE;
    private long lookbackCandles = DEFAULT_LOOKBACK_CANDLES;

    public BarVolumeCappedQuantity(TradeQuantity delegate, ReadCandleRepository candleRepository) {
        this.delegate = delegate;
        this.candleRepository = candleRepository;
    }

    public double getBarVolumeShare() {
        return barVolumeShare;
    }

    /**
     * The share of a typical bar a single order may ask for. Must be a share - past a whole bar the
     * order stops fitting into one, which is the only thing this cap is for.
     */
    public BarVolumeCappedQuantity setBarVolumeShare(double barVolumeShare) {
        if (barVolumeShare <= 0 || barVolumeShare > 1) {
            throw new IllegalArgumentException(
                String.format("Bar volume share must be within (0, 1], got %s", barVolumeShare)
            );
        }

        this.barVolumeShare = barVolumeShare;
        return this;
    }

    public long getLookbackCandles() {
        return lookbackCandles;
    }

    public BarVolumeCappedQuantity setLookbackCandles(long lookbackCandles) {
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

        long cap = barVolumeCap(moment);

        return cap <= 0 ? wanted : Math.min(wanted, cap);
    }

    /**
     * A share of what a bar of this instrument typically trades, averaged over the recent ones.
     * <p>
     * Averaged rather than taken from the last bar, because the order will fill against the bars
     * that come <em>after</em> the decision, and a single bar is a noisy guess at those. The last
     * bar being unusually busy is not a reason to send an order the next one cannot absorb.
     */
    protected long barVolumeCap(TradeMoment moment) {
        List<Candle> candles = candleRepository.findBeforeOrEqual(
            moment.instrumentId(),
            moment.at(),
            lookbackCandles
        );

        if (candles.isEmpty()) {
            return 0;
        }

        long totalVolume = 0;

        for (Candle candle : candles) {
            totalVolume += candle.volume();
        }

        return (long) (((double) totalVolume / candles.size()) * barVolumeShare);
    }
}
