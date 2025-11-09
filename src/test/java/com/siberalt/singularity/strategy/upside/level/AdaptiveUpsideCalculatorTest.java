package com.siberalt.singularity.strategy.upside.level;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.level.Level;
import com.siberalt.singularity.strategy.market.CandleIndexProvider;
import com.siberalt.singularity.strategy.upside.Upside;
import com.siberalt.singularity.strategy.upside.UpsideCalculator;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

class AdaptiveUpsideCalculatorTest {

    @Test
    void calculatesUpsideWithBalancedWeights() {
        LevelBasedUpsideCalculator levelsCalculator = Mockito.mock(LevelBasedUpsideCalculator.class);
        UpsideCalculator volumeCalculator = Mockito.mock(UpsideCalculator.class);
        CandleIndexProvider candleIndexProvider = Mockito.mock(CandleIndexProvider.class);

        AdaptiveUpsideCalculator calculator = new AdaptiveUpsideCalculator(levelsCalculator, volumeCalculator);

        Level<Double> resistance = new Level<>(null, null, 0, 0, x -> 100.0, 0.0);
        Level<Double> support = new Level<>(null, null, 0, 0, x -> 90.0, 0.0);
        List<Candle> recentCandles = List.of(
            Candle.of(Instant.parse("2024-01-01T00:00:00Z"), 60, 95.0)
        );

        when(levelsCalculator.calculate(resistance, support, recentCandles, candleIndexProvider))
            .thenReturn(new Upside(0.5, 0.7));
        when(volumeCalculator.calculate(recentCandles))
            .thenReturn(new Upside(0.3, 0.6));

        Upside result = calculator.calculate(resistance, support, recentCandles, candleIndexProvider);

        assertEquals(0.42, result.signal(), 0.01);
        assertEquals(0.66, result.strength(), 0.01);
    }

    @Test
    void adjustsWeightsNearKeyLevelWithStrongVolumeSignal() {
        LevelBasedUpsideCalculator levelsCalculator = Mockito.mock(LevelBasedUpsideCalculator.class);
        UpsideCalculator volumeCalculator = Mockito.mock(UpsideCalculator.class);
        CandleIndexProvider candleIndexProvider = Mockito.mock(CandleIndexProvider.class);

        AdaptiveUpsideCalculator calculator = new AdaptiveUpsideCalculator(levelsCalculator, volumeCalculator);

        Level<Double> resistance = new Level<>(null, null, 0, 0, x -> 100.0, 0.0);
        Level<Double> support = new Level<>(null, null, 0, 0, x -> 90.0, 0.0);
        List<Candle> recentCandles = List.of(
            Candle.of(Instant.parse("2024-01-01T00:00:00Z"), 60, 100.5)
        );

        when(candleIndexProvider.provideIndex(recentCandles.get(0))).thenReturn(0L);
        when(levelsCalculator.calculate(resistance, support, recentCandles, candleIndexProvider))
            .thenReturn(new Upside(0.5, 0.7));
        when(volumeCalculator.calculate(recentCandles))
            .thenReturn(new Upside(0.8, 0.9));

        Upside result = calculator.calculate(resistance, support, recentCandles, candleIndexProvider);

        assertEquals(0.71, result.signal(), 0.01);
        assertEquals(0.84, result.strength(), 0.01);
    }

    @Test
    void normalizesWeightsWhenSignalsDiverge() {
        LevelBasedUpsideCalculator levelsCalculator = Mockito.mock(LevelBasedUpsideCalculator.class);
        UpsideCalculator volumeCalculator = Mockito.mock(UpsideCalculator.class);
        CandleIndexProvider candleIndexProvider = Mockito.mock(CandleIndexProvider.class);

        AdaptiveUpsideCalculator calculator = new AdaptiveUpsideCalculator(levelsCalculator, volumeCalculator);

        Level<Double> resistance = new Level<>(null, null, 0, 0, x -> 100.0, 0.0);
        Level<Double> support = new Level<>(null, null, 0, 0, x -> 90.0, 0.0);
        List<Candle> recentCandles = List.of(
            Candle.of(Instant.parse("2024-01-01T00:00:00Z"), 60, 95.0)
        );

        when(levelsCalculator.calculate(resistance, support, recentCandles, candleIndexProvider))
            .thenReturn(new Upside(0.5, 0.7));
        when(volumeCalculator.calculate(recentCandles))
            .thenReturn(new Upside(-0.8, 0.9));

        Upside result = calculator.calculate(resistance, support, recentCandles, candleIndexProvider);

        assertEquals(-0.34, result.signal(), 0.01);
        assertEquals(0.83, result.strength(), 0.01);
    }
}
