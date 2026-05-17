package com.siberalt.singularity.entity.candle;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;

public class Candle {
    public static final long DEFAULT_INDEX = -1;

    private String instrumentUid;
    private TimePoint timePoint;
    private Quotation open;
    private Quotation close;
    private Quotation high;
    private Quotation low;
    private long volume;

    public Candle() {
    }

    public Candle(
        String instrumentUid,
        TimePoint timePoint,
        Quotation openPrice,
        Quotation closePrice,
        Quotation highPrice,
        Quotation lowPrice,
        long volume
    ) {
        this.instrumentUid = instrumentUid;
        this.timePoint = timePoint;
        this.open = openPrice;
        this.close = closePrice;
        this.high = highPrice;
        this.low = lowPrice;
        this.volume = volume;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Candle candle = (Candle) o;
        return volume == candle.volume &&
            Objects.equals(instrumentUid, candle.instrumentUid) &&
            Objects.equals(timePoint, candle.timePoint) &&
            Objects.equals(open, candle.open) &&
            Objects.equals(close, candle.close) &&
            Objects.equals(high, candle.high) &&
            Objects.equals(low, candle.low);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            instrumentUid,
            timePoint.toString(),
            open.toString(),
            close.toString(),
            high.toString(),
            low.toString(),
            volume
        );
    }

    public long getIndex() {
        return timePoint.index();
    }

    public Candle setIndex(long index) {
        timePoint = (timePoint == null) ? new TimePoint(index, null) : new TimePoint(index, timePoint.time());
        return this;
    }

    public String getInstrumentUid() {
        return instrumentUid;
    }

    public Candle setInstrumentUid(String instrumentUid) {
        this.instrumentUid = instrumentUid;
        return this;
    }

    public Instant getTime() {
        return timePoint.time();
    }

    public Candle setTime(Instant time) {
        timePoint = (timePoint == null) ? new TimePoint(DEFAULT_INDEX, time) : new TimePoint(timePoint.index(), time);
        return this;
    }

    public Quotation getOpen() {
        return open;
    }

    public double getOpenAsDouble(){
        return this.getOpen().toDouble();
    }

    public Candle setOpen(Quotation open) {
        this.open = open;
        return this;
    }

    public Quotation getClose() {
        return close;
    }

    public double getCloseAsDouble() {
        return close.toDouble();
    }

    public Candle setClose(Quotation close) {
        this.close = close;
        return this;
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

    public Quotation getHigh() {
        return high;
    }

    public double getHighAsDouble() {
        return this.getHigh().toDouble();
    }

    public Candle setHigh(Quotation high) {
        this.high = high;
        return this;
    }

    public Quotation getLow() {
        return low;
    }

    public double getLowAsDouble(){
        return this.getLow().toDouble();
    }

    public Candle setLow(Quotation low) {
        this.low = low;
        return this;
    }

    public long getVolume() {
        return volume;
    }

    public Candle setVolume(long volume) {
        this.volume = volume;
        return this;
    }

    public TimePoint getTimePoint() {
        return timePoint;
    }

    public Candle setTimePoint(TimePoint timePoint) {
        this.timePoint = timePoint;
        return this;
    }

    @Override
    public Candle clone() {
        return new Candle()
            .setInstrumentUid(instrumentUid)
            .setTimePoint(timePoint)
            .setOpen(open)
            .setClose(close)
            .setHigh(high)
            .setLow(low)
            .setVolume(volume);
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

    public static Candle of(TimePoint timePoint, String instrumentUid, long volume, double open, double high, double low, double close) {
        return new Candle()
            .setInstrumentUid(instrumentUid)
            .setTimePoint(timePoint)
            .setVolume(volume)
            .setOpen(Quotation.of(open))
            .setHigh(Quotation.of(high))
            .setLow(Quotation.of(low))
            .setClose(Quotation.of(close));
    }

    public static Candle of(TimePoint timePoint, long volume, double open, double high, double low, double close) {
        return new Candle()
            .setTimePoint(timePoint)
            .setVolume(volume)
            .setOpen(Quotation.of(open))
            .setHigh(Quotation.of(high))
            .setLow(Quotation.of(low))
            .setClose(Quotation.of(close));
    }

    public static Candle of(Instant time, String instrumentUid, long volume, double open, double high, double low, double close) {
        return new Candle()
            .setInstrumentUid(instrumentUid)
            .setTime(time)
            .setVolume(volume)
            .setOpen(Quotation.of(open))
            .setHigh(Quotation.of(high))
            .setLow(Quotation.of(low))
            .setClose(Quotation.of(close));
    }

    public static Candle of(Instant time, String instrumentUid, long volume, Quotation open, Quotation high, Quotation low, Quotation close) {
        return new Candle()
            .setInstrumentUid(instrumentUid)
            .setTime(time)
            .setVolume(volume)
            .setOpen(open)
            .setHigh(high)
            .setLow(low)
            .setClose(close);
    }

    public static Candle of(Instant time, long volume, double open, double high, double low, double close) {
        return new Candle()
            .setTime(time)
            .setVolume(volume)
            .setOpen(Quotation.of(open))
            .setHigh(Quotation.of(high))
            .setLow(Quotation.of(low))
            .setClose(Quotation.of(close));
    }

    public static CandleBuilder builder() {
        return new CandleBuilder();
    }

    public static CandleFactory factory(String instrumentUid, long startIndex) {
        return new CandleFactory(instrumentUid, startIndex);
    }
}
