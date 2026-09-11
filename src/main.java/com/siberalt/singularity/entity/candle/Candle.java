package com.siberalt.singularity.entity.candle;

import com.siberalt.singularity.broker.contract.service.order.request.OrderDirection;
import com.siberalt.singularity.broker.contract.value.quotation.Quotation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;

public record Candle (
    String instrumentUid,
    TimePoint timePoint,
    Quotation open,
    Quotation close,
    Quotation high,
    Quotation low,
    long volume,
    long volumeBuy,
    long volumeSell
){
    public static final long DEFAULT_INDEX = -1;

    public Candle(
        String instrumentUid,
        TimePoint timePoint,
        Quotation open,
        Quotation close,
        Quotation high,
        Quotation low,
        long volume
    ) {
        this(instrumentUid, timePoint, open, close, high, low, volume, 0L, 0L);
    }

    public static final Candle EMPTY = new Candle(
        null,
        TimePoint.NULL,
        Quotation.ZERO,
        Quotation.ZERO,
        Quotation.ZERO,
        Quotation.ZERO,
        0
    );

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Candle candle = (Candle) o;
        return volume == candle.volume &&
            volumeBuy == candle.volumeBuy &&
            volumeSell == candle.volumeSell &&
            Objects.equals(instrumentUid, candle.instrumentUid) &&
            Objects.equals(timePoint, candle.timePoint) &&
            Objects.equals(open, candle.open) &&
            Objects.equals(close, candle.close) &&
            Objects.equals(high, candle.high) &&
            Objects.equals(low, candle.low);
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(instrumentUid);
        result = 31 * result + Objects.hashCode(timePoint);
        result = 31 * result + Objects.hashCode(open);
        result = 31 * result + Objects.hashCode(close);
        result = 31 * result + Objects.hashCode(high);
        result = 31 * result + Objects.hashCode(low);
        result = 31 * result + Long.hashCode(volume);
        result = 31 * result + Long.hashCode(volumeBuy);
        result = 31 * result + Long.hashCode(volumeSell);
        return result;
    }

    public long getIndex() {
        return timePoint.index();
    }

    public Instant getTime() {
        return timePoint.time();
    }

    public double getOpenAsDouble(){
        return this.open().toDouble();
    }

    public double getCloseAsDouble() {
        return close.toDouble();
    }

    public double getTypicalAsDouble() {
        Quotation typicalPrice = getTypical();
        return typicalPrice != null ? typicalPrice.toDouble() : Double.NaN;
    }

    public Quotation getTypical() {
        if (close == null || high == null || low == null) {
            return null;
        }
        return Quotation.of(
            close
                .add(high.toBigDecimal())
                .add(low.toBigDecimal())
                .divide(BigDecimal.valueOf(3), RoundingMode.HALF_EVEN)
        );
    }

    public double getHighAsDouble() {
        return this.high().toDouble();
    }

    public double getLowAsDouble(){
        return this.low().toDouble();
    }

    /**
     * How much of this bar an order taking liquidity in this direction could have reached: the side
     * of the flow it trades against, or the whole bar where the split is not recorded.
     * <p>
     * A buy takes what the offers hold and a sell takes what the bids hold, so a bar whose whole
     * volume was sell-initiated had nothing in it for a buyer. Both the cap on how much a bar can
     * absorb and the impact charged for taking it are shares of this number, and they have to be
     * shares of the same one - measuring the cap against one side and the impact against both left
     * the impact understated by about half.
     */
    public long reachableVolume(OrderDirection direction) {
        if (!hasVolumeSplit() || direction == null || direction == OrderDirection.UNSPECIFIED) {
            return tradedVolume();
        }

        return direction == OrderDirection.BUY ? volumeBuy : volumeSell;
    }

    /**
     * Whether this candle carries its volume split into buy- and sell-initiated trades. The feed
     * only started reporting the split partway through, so anything reading it has to ask first -
     * an absent split reads as no flow either way, which is not the same as balanced flow.
     * <p>
     * The test is the sum, not each side. A bar where every trade went one way has a zero on the
     * other side and is the most informative bar there is; requiring both to be positive would
     * throw away nearly half the history of an instrument, and precisely its one-sided half.
     */
    public boolean hasVolumeSplit() {
        return volumeBuy + volumeSell > 0;
    }

    /**
     * How much traded, taken from the split where there is one and from the plain total otherwise.
     * The two agree wherever both are recorded.
     */
    public long tradedVolume() {
        return hasVolumeSplit() ? volumeBuy + volumeSell : volume;
    }

    /**
     * Buy-initiated volume less sell-initiated: which side was taking liquidity, and by how much.
     * Zero when the candle has no split - unknown flow, reported as no imbalance, so a caller that
     * skips {@link #hasVolumeSplit()} gets a signal that says nothing rather than a wrong one.
     */
    public long netVolume() {
        return hasVolumeSplit() ? volumeBuy - volumeSell : 0;
    }

    /**
     * The same imbalance as a share of what traded, in [-1, 1]: +1 is a bar where every trade was a
     * buy, -1 one where every trade was a sell. Comparable across bars and across instruments,
     * which the raw difference is not.
     */
    public double volumeImbalance() {
        return hasVolumeSplit() ? (double) netVolume() / tradedVolume() : 0;
    }

    @Override
    public Candle clone() {
        return new Candle(instrumentUid, timePoint, open, close, high, low, volume, volumeBuy, volumeSell);
    }

    public boolean isEmpty() {
        return open == null && close == null && high == null && low == null && volume == 0;
    }

    public static Candle of(Instant time, String instrumentUid, long volume, double repeatedValue) {
        return Candle.of(time, instrumentUid, volume, repeatedValue, repeatedValue, repeatedValue, repeatedValue);
    }

    public static Candle of(Instant time, long volume, double repeatedValue) {
        return Candle.of(time, volume, repeatedValue, repeatedValue, repeatedValue, repeatedValue);
    }

    public static Candle of(Instant time, double repeatedValue) {
        return Candle.of(time, 0, repeatedValue, repeatedValue, repeatedValue, repeatedValue);
    }

    public static Candle of(TimePoint timePoint, double repeatedValue) {
        return Candle.of(timePoint, 0L, repeatedValue, repeatedValue, repeatedValue, repeatedValue);
    }

    public static Candle of(
        TimePoint timePoint, 
        String instrumentUid, 
        long volume,
        Quotation open, 
        Quotation high,
        Quotation low,
        Quotation close
    ) {
        return new Candle(instrumentUid, timePoint, open, close, high, low, volume);
    }

    public static Candle of(TimePoint timePoint, String instrumentUid, long volume, double open, double high, double low, double close) {
        return new Candle(
            instrumentUid,
            timePoint,
            Quotation.of(open),
            Quotation.of(close),
            Quotation.of(high),
            Quotation.of(low),
            volume
        );
    }

    public static Candle of(TimePoint timePoint, long volume, double open, double high, double low, double close) {
        return new Candle(
            null,
            timePoint,
            Quotation.of(open),
            Quotation.of(close),
            Quotation.of(high),
            Quotation.of(low),
            volume
        );
    }

    public static Candle of(Instant time, String instrumentUid, long volume, double open, double high, double low, double close) {
        return new Candle(
            instrumentUid,
            new TimePoint(time),
            Quotation.of(open),
            Quotation.of(close),
            Quotation.of(high),
            Quotation.of(low),
            volume
        );
    }

    public static Candle of(Instant time, String instrumentUid, long volume, Quotation open, Quotation high, Quotation low, Quotation close) {
        return new Candle(
            instrumentUid,
            new TimePoint(time),
            open,
            close,
            high,
            low,
            volume
        );
    }

    public static Candle of(Instant time, long volume, double open, double high, double low, double close) {
        return new Candle(
            null,
            new TimePoint(time),
            Quotation.of(open),
            Quotation.of(close),
            Quotation.of(high),
            Quotation.of(low),
            volume
        );
    }

    public static CandleBuilder builder() {
        return new CandleBuilder();
    }

    public static CandleFactory factory(String instrumentUid, long startIndex) {
        return new CandleFactory(instrumentUid, startIndex);
    }
}
