package com.siberalt.singularity.presenter.google.series;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.CandleFactory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CandleSeriesTest {
    private final CandleFactory candleFactory = new CandleFactory("EURUSD");

    @Test
    void provideReturnsEmptyWhenNoCandles() {
        CandleSeriesProvider provider = new CandleSeriesProvider(List.of());
        Optional<SeriesChunk> result = provider.provide(0, 1000, 100);
        assertTrue(result.isEmpty());
    }

    @Test
    void provideReturnsCorrectDataForValidCandles() {
        List<Candle> candles = List.of(
            candleFactory.createCommon("2023-01-01T00:01:00Z", 87, 100.0),
            candleFactory.createCommon("2023-01-01T00:02:00Z", 88, 101.0),
            candleFactory.createCommon("2023-01-01T00:03:00Z", 89, 102.0)
        );

        CandleSeriesProvider provider = new CandleSeriesProvider(candles);
        Optional<SeriesChunk> result = provider.provide(0, 2, 1);

        assertTrue(result.isPresent());
        SeriesChunk chunk = result.get();

        assertEquals(2, chunk.columns().size());
        assertEquals(3, chunk.data().length);
        assertEquals("2023-01-01T00:01:00Z", chunk.data()[0][0]);
        assertEquals(100.0, chunk.data()[0][1]);
        assertEquals("2023-01-01T00:02:00Z", chunk.data()[1][0]);
        assertEquals(101.0, chunk.data()[1][1]);
    }

    @Test
    void provideSkipsCandlesOutsideRange() {
        List<Candle> candles = List.of(
            candleFactory.createCommon("2023-01-01T00:01:00Z", 100, 100.0),
            candleFactory.createCommon("2023-01-01T00:02:00Z", 100, 101.0),
            candleFactory.createCommon("2023-01-01T00:03:00Z", 100, 102.0)
        );

        CandleSeriesProvider series = new CandleSeriesProvider(candles);
        Optional<SeriesChunk> result = series.provide(0, 1, 1);

        assertTrue(result.isPresent());
        SeriesChunk chunk = result.get();

        assertEquals(2, chunk.data().length);
        assertEquals("2023-01-01T00:01:00Z", chunk.data()[0][0]);
        assertEquals(100.0, chunk.data()[0][1]);
        assertEquals("2023-01-01T00:02:00Z", chunk.data()[1][0]);
        assertEquals(101.0, chunk.data()[1][1]);
    }

    @Test
    void provideHandlesStepGreaterThanOne() {
        List<Candle> candles = List.of(
            candleFactory.createCommon("2023-01-01T00:01:00Z", 87, 100.0),
            candleFactory.createCommon("2023-01-01T00:02:00Z", 88, 101.0),
            candleFactory.createCommon("2023-01-01T00:03:00Z", 89, 102.0),
            candleFactory.createCommon("2023-01-01T00:04:00Z", 90, 103.0)
        );

        CandleSeriesProvider provider = new CandleSeriesProvider(candles);
        Optional<SeriesChunk> result = provider.provide(0, 3, 2);

        assertTrue(result.isPresent());
        SeriesChunk chunk = result.get();

        assertEquals(2, chunk.data().length); // Only every second candle is included
        assertEquals("2023-01-01T00:01:00Z", chunk.data()[0][0]);
        assertEquals(100.0, chunk.data()[0][1]);
        assertEquals("2023-01-01T00:03:00Z", chunk.data()[1][0]);
        assertEquals(102.0, chunk.data()[1][1]);
    }

    @Test
    void provideAppliesCustomPriceExtractor() {
        List<Candle> candles = List.of(
            candleFactory.createCommon("2023-01-01T00:00:00Z", 0, 100.0),
            candleFactory.createCommon("2023-01-01T00:01:00Z", 1, 101.0)
        );

        CandleSeriesProvider series = new CandleSeriesProvider(candles, c -> c.getCloseAsDouble() * 2);
        Optional<SeriesChunk> result = series.provide(0, 1, 1);

        assertTrue(result.isPresent());
        SeriesChunk chunk = result.get();

        assertEquals(2, chunk.data().length);
        assertEquals(200.0, chunk.data()[0][1]);
        assertEquals(202.0, chunk.data()[1][1]);
    }

    @Test
    void provideHandlesEmptyDataArrayGracefully() {
        List<Candle> candles = List.of();
        CandleSeriesProvider series = new CandleSeriesProvider(candles);

        Optional<SeriesChunk> result = series.provide(0, 1000, 100);
        assertTrue(result.isEmpty());
    }
}