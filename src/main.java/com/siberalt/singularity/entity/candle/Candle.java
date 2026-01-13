package com.siberalt.singularity.entity.candle;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;
import java.util.stream.Stream;

public class Candle {
    private String instrumentUid;
    private Instant time;
    private Quotation openPrice;
    private Quotation closePrice;
    private Quotation highPrice;
    private Quotation lowPrice;
    private long volume;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Candle candle = (Candle) o;
        return volume == candle.volume &&
            Objects.equals(instrumentUid, candle.instrumentUid) &&
            Objects.equals(time, candle.time) &&
            Objects.equals(openPrice, candle.openPrice) &&
            Objects.equals(closePrice, candle.closePrice) &&
            Objects.equals(highPrice, candle.highPrice) &&
            Objects.equals(lowPrice, candle.lowPrice);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            instrumentUid,
            time.toString(),
            openPrice.toString(),
            closePrice.toString(),
            highPrice.toString(),
            lowPrice.toString(),
            volume
        );
    }

    public String getInstrumentUid() {
        return instrumentUid;
    }

    public Candle setInstrumentUid(String instrumentUid) {
        this.instrumentUid = instrumentUid;
        return this;
    }

    public Instant getTime() {
        return time;
    }

    public Candle setTime(Instant time) {
        this.time = time;
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

    @Override
    public Candle clone() {
        return new Candle()
            .setInstrumentUid(instrumentUid)
            .setTime(time)
            .setOpenPrice(openPrice)
            .setClosePrice(closePrice)
            .setHighPrice(highPrice)
            .setLowPrice(lowPrice)
            .setVolume(volume);
    }

    public boolean isEmpty() {
        return openPrice == null && closePrice == null && highPrice == null && lowPrice == null && volume == 0;
    }

    public Candle merge(Candle other) {
        if (other == null || other.isEmpty()) {
            return this;
        }
        return new Candle()
            .setInstrumentUid(instrumentUid)
            .setTime(time)
            .setOpenPrice(openPrice != null ? openPrice : other.openPrice)
            .setClosePrice(closePrice != null ? closePrice : other.closePrice)
            .setHighPrice(highPrice != null ? highPrice : other.highPrice)
            .setLowPrice(lowPrice != null ? lowPrice : other.lowPrice)
            .setVolume(volume + other.volume);
    }

    public Candle add(Candle other) {
        if (other == null) {
            return this;
        }
        return new Candle()
            .setInstrumentUid(instrumentUid)
            .setTime(time)
            .setOpenPrice(openPrice.add(other.openPrice))
            .setClosePrice(closePrice.add(other.closePrice))
            .setHighPrice(highPrice.add(other.highPrice))
            .setLowPrice(lowPrice.add(other.lowPrice))
            .setVolume(volume + other.volume);
    }

    public Candle divide(int divisor) {
        if (divisor <= 0) {
            throw new IllegalArgumentException("Divisor must be greater than zero");
        }
        return new Candle()
            .setInstrumentUid(instrumentUid)
            .setTime(time)
            .setOpenPrice(openPrice.divide(divisor))
            .setClosePrice(closePrice.divide(divisor))
            .setHighPrice(highPrice.divide(divisor))
            .setLowPrice(lowPrice.divide(divisor))
            .setVolume(volume / divisor);
    }

    /**
     * Calculates the average price of the candle using the formula:
     * (lowPrice + highPrice + closePrice + openPrice) / 4
     *
     * @return The average price as a Quotation.
     */
    public Quotation getAveragePrice() {
        return getAveragePrice(RoundingMode.HALF_EVEN);
    }

    public Quotation getAveragePrice(RoundingMode roundingMode) {
        var sum = Stream.of(lowPrice, highPrice, closePrice, openPrice)
            .map(Objects::requireNonNull)
            .map(Quotation::toBigDecimal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return Quotation.of(sum.divide(BigDecimal.valueOf(4), roundingMode));
    }

    public static Candle of(Instant time, long volume, Quotation open, Quotation high, Quotation low, Quotation close) {
        return new Candle()
            .setTime(time)
            .setVolume(volume)
            .setOpenPrice(open)
            .setHighPrice(high)
            .setLowPrice(low)
            .setClosePrice(close);
    }

    public static Candle of(Instant time, String instrumentUid, double repeatedValue) {
        return Candle.of(time, instrumentUid, 0, repeatedValue, repeatedValue, repeatedValue, repeatedValue);
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
}
