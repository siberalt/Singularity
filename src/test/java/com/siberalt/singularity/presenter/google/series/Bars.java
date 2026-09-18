package com.siberalt.singularity.presenter.google.series;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.TimePoint;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Minute bars for the chart tests, numbered from zero so that a candle's index, its position and its
 * row are the same number - the case every series was written against before there was an axis.
 */
final class Bars {
    static final Instant START = Instant.parse("2025-01-02T07:00:00Z");

    private Bars() {
    }

    /** Candles with indices {@code 0..count-1}, a minute apart, priced 100 upwards. */
    static List<Candle> candles(int count) {
        List<Candle> candles = new ArrayList<>();

        for (int minute = 0; minute < count; minute++) {
            candles.add(Candle.of(new TimePoint(minute, START.plusSeconds(60L * minute)), 100 + minute));
        }

        return candles;
    }

    /** An axis of {@code count} minute bars, so row {@code n} is the bar of index {@code n}. */
    static BarAxis minutes(int count) {
        return BarAxis.ofBars(candles(count));
    }
}
