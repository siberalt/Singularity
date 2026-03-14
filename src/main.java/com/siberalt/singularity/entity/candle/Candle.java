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
    private Quotation openPrice;
    private Quotation closePrice;
    private Quotation highPrice;
    private Quotation lowPrice;
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
        this.openPrice = openPrice;
        this.closePrice = closePrice;
        this.highPrice = highPrice;
        this.lowPrice = lowPrice;
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
            Objects.equals(openPrice, candle.openPrice) &&
            Objects.equals(closePrice, candle.closePrice) &&
            Objects.equals(highPrice, candle.highPrice) &&
            Objects.equals(lowPrice, candle.lowPrice);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            instrumentUid,
            timePoint.toString(),
            openPrice.toString(),
            closePrice.toString(),
            highPrice.toString(),
            lowPrice.toString(),
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

    public Quotation getOpenPrice() {
        return openPrice;
    }

    public Candle setOpenPrice(Quotation openPrice) {
        this.openPrice = openPrice;
        return this;
    }

    public Quotation getClosePrice() {
        return closePrice;
    }

    public Candle setClosePrice(Quotation closePrice) {
        this.closePrice = closePrice;
        return this;
    }

    public double getTypicalPriceAsDouble() {
        Quotation typicalPrice = getTypicalPrice();
        return typicalPrice != null ? typicalPrice.toDouble() : Double.NaN;
    }

    public Quotation getTypicalPrice() {
        if (closePrice == null || highPrice == null || lowPrice == null) {
            return null;
        }
        return Quotation.of(
            closePrice
                .add(highPrice.toBigDecimal())
                .add(lowPrice.toBigDecimal())
                .divide(BigDecimal.valueOf(3), RoundingMode.HALF_EVEN)
        );
    }

    public Quotation getHighPrice() {
        return highPrice;
    }

    public Candle setHighPrice(Quotation highPrice) {
        this.highPrice = highPrice;
        return this;
    }

    public Quotation getLowPrice() {
        return lowPrice;
    }

    public Candle setLowPrice(Quotation lowPrice) {
        this.lowPrice = lowPrice;
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
            .setOpenPrice(openPrice)
            .setClosePrice(closePrice)
            .setHighPrice(highPrice)
            .setLowPrice(lowPrice)
            .setVolume(volume);
    }

    public boolean isEmpty() {
        return openPrice == null && closePrice == null && highPrice == null && lowPrice == null && volume == 0;
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
            .setOpenPrice(Quotation.of(open))
            .setHighPrice(Quotation.of(high))
            .setLowPrice(Quotation.of(low))
            .setClosePrice(Quotation.of(close));
    }

    public static Candle of(TimePoint timePoint, long volume, double open, double high, double low, double close) {
        return new Candle()
            .setTimePoint(timePoint)
            .setVolume(volume)
            .setOpenPrice(Quotation.of(open))
            .setHighPrice(Quotation.of(high))
            .setLowPrice(Quotation.of(low))
            .setClosePrice(Quotation.of(close));
    }

    public static Candle of(Instant time, String instrumentUid, long volume, double open, double high, double low, double close) {
        return new Candle()
            .setInstrumentUid(instrumentUid)
            .setTime(time)
            .setVolume(volume)
            .setOpenPrice(Quotation.of(open))
            .setHighPrice(Quotation.of(high))
            .setLowPrice(Quotation.of(low))
            .setClosePrice(Quotation.of(close));
    }

    public static Candle of(Instant time, String instrumentUid, long volume, Quotation open, Quotation high, Quotation low, Quotation close) {
        return new Candle()
            .setInstrumentUid(instrumentUid)
            .setTime(time)
            .setVolume(volume)
            .setOpenPrice(open)
            .setHighPrice(high)
            .setLowPrice(low)
            .setClosePrice(close);
    }

    public static Candle of(Instant time, long volume, double open, double high, double low, double close) {
        return new Candle()
            .setTime(time)
            .setVolume(volume)
            .setOpenPrice(Quotation.of(open))
            .setHighPrice(Quotation.of(high))
            .setLowPrice(Quotation.of(low))
            .setClosePrice(Quotation.of(close));
    }

    public static CandleBuilder builder() {
        return new CandleBuilder();
    }

    public static CandleFactory factory(String instrumentUid, long startIndex) {
        return new CandleFactory(instrumentUid, startIndex);
    }
}
