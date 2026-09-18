package com.siberalt.singularity.presenter.google.series;

import com.siberalt.singularity.entity.candle.Candle;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CandleSeriesTest {
    @Test
    void provideReturnsEmptyWhenNoCandles() {
        CandleSeriesProvider provider = new CandleSeriesProvider(List.of());

        assertTrue(provider.provide(Bars.minutes(10), 1).isEmpty());
    }

    @Test
    void provideReturnsCorrectDataForValidCandles() {
        List<Candle> candles = Bars.candles(3);
        CandleSeriesProvider provider = new CandleSeriesProvider(candles);

        Optional<SeriesChunk> result = provider.provide(BarAxis.ofBars(candles), 1);

        assertTrue(result.isPresent());
        SeriesChunk chunk = result.get();

        assertEquals(2, chunk.columns().size());
        assertEquals(3, chunk.data().length);
        assertEquals(Bars.START.toString(), chunk.data()[0][0]);
        assertEquals(100.0, chunk.data()[0][1]);
        assertEquals(Bars.START.plusSeconds(60).toString(), chunk.data()[1][0]);
        assertEquals(101.0, chunk.data()[1][1]);
    }

    @Test
    void provideSkipsCandlesOutsideTheAxis() {
        List<Candle> candles = Bars.candles(3);
        CandleSeriesProvider series = new CandleSeriesProvider(candles);

        // An axis of the first two bars only: the third candle has nowhere to go.
        Optional<SeriesChunk> result = series.provide(BarAxis.ofBars(candles.subList(0, 2)), 1);

        assertTrue(result.isPresent());
        SeriesChunk chunk = result.get();

        assertEquals(2, chunk.data().length);
        assertEquals(100.0, chunk.data()[0][1]);
        assertEquals(101.0, chunk.data()[1][1]);
    }

    @Test
    void provideHandlesStepGreaterThanOne() {
        List<Candle> candles = Bars.candles(4);
        CandleSeriesProvider provider = new CandleSeriesProvider(candles);

        Optional<SeriesChunk> result = provider.provide(BarAxis.ofBars(candles), 2);

        assertTrue(result.isPresent());
        SeriesChunk chunk = result.get();

        assertEquals(2, chunk.data().length); // Only every second bar is drawn
        assertEquals(Bars.START.toString(), chunk.data()[0][0]);
        assertEquals(100.0, chunk.data()[0][1]);
        assertEquals(Bars.START.plusSeconds(120).toString(), chunk.data()[1][0]);
        assertEquals(102.0, chunk.data()[1][1]);
    }

    @Test
    void provideAppliesCustomPriceExtractor() {
        List<Candle> candles = Bars.candles(2);
        CandleSeriesProvider series = new CandleSeriesProvider(candles, candle -> candle.getCloseAsDouble() * 2);

        Optional<SeriesChunk> result = series.provide(BarAxis.ofBars(candles), 1);

        assertTrue(result.isPresent());
        SeriesChunk chunk = result.get();

        assertEquals(2, chunk.data().length);
        assertEquals(200.0, chunk.data()[0][1]);
        assertEquals(202.0, chunk.data()[1][1]);
    }

    @Test
    void provideHandlesAnEmptyAxisGracefully() {
        CandleSeriesProvider series = new CandleSeriesProvider(Bars.candles(3));

        assertTrue(series.provide(Bars.minutes(0), 1).isEmpty());
    }
}
