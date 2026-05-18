package com.siberalt.singularity.entity.candle;

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
    long volume
){
    public static final long DEFAULT_INDEX = -1;

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

    @Override
    public Candle clone() {
        return new Candle(instrumentUid, timePoint, open, close, high, low, volume);
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
