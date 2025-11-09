package com.siberalt.singularity.strategy.upside.level;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.level.Level;
import com.siberalt.singularity.strategy.level.LevelDetector;
import com.siberalt.singularity.strategy.level.selector.LevelPair;
import com.siberalt.singularity.strategy.level.selector.LevelSelector;
import com.siberalt.singularity.strategy.market.CumulativeCandleIndexProvider;
import com.siberalt.singularity.strategy.upside.Upside;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

public class KeyLevelsUpsideCalculatorTest {
    private final CumulativeCandleIndexProvider candleIndexProvider = mock(CumulativeCandleIndexProvider.class);

    @Test
    void calculatesUpsideWhenSupportAndResistanceAreDetectedWithDifferentValues() {
        Level<Double> support = new Level<>(null, null, 20L, 20L, x -> 60.0, 0.0);
        Level<Double> resistance = new Level<>(null, null, 25L, 25L, x -> 120.0, 0.0);
        Candle lastCandle = createCandle(90.0);

        LevelDetector<Double> supportDetector = mock(LevelDetector.class);
        LevelDetector<Double> resistanceDetector = mock(LevelDetector.class);
        LevelBasedUpsideCalculator upsideCalculator = mock(LevelBasedUpsideCalculator.class);
        LevelSelector levelSelector = mock(LevelSelector.class);

        when(levelSelector.select(any(), any(), any(), any()))
            .thenReturn(List.of(new LevelPair(resistance, support)));
        doReturn(List.of(support)).when(supportDetector).detect(anyList(), any());
        doReturn(List.of(resistance)).when(resistanceDetector).detect(anyList(), any());
        when(upsideCalculator.calculate(resistance, support, List.of(lastCandle), candleIndexProvider))
            .thenReturn(new Upside(0.7, 1.5));

        KeyLevelsUpsideCalculator calculator = new KeyLevelsUpsideCalculator(
            supportDetector,
            resistanceDetector,
            upsideCalculator
        );
        calculator.setLevelSelector(levelSelector);
        calculator.setCandleIndexProvider(candleIndexProvider);
        Upside result = calculator.calculate(List.of(lastCandle));

        assertNotNull(result);
        assertEquals(0.7, result.signal(), 0.01);
        assertEquals(1.5, result.strength(), 0.01);
    }
    @Test
    void returnsZeroUpsideWhenNoSupportOrResistanceIsDetected() {
        Candle lastCandle = Candle.of(Instant.now(), 1000L, 75);

        LevelDetector<Double> supportDetector = mock(LevelDetector.class);
        LevelDetector<Double> resistanceDetector = mock(LevelDetector.class);
        LevelBasedUpsideCalculator upsideCalculator = mock(LevelBasedUpsideCalculator.class);

        when(supportDetector.detect(anyList(), any())).thenReturn(List.of());
        when(resistanceDetector.detect(anyList(), any())).thenReturn(List.of());

        KeyLevelsUpsideCalculator calculator = new KeyLevelsUpsideCalculator(
            supportDetector,
            resistanceDetector,
            upsideCalculator
        );
        Upside result = calculator.calculate(List.of(lastCandle));

        assertNotNull(result);
        assertEquals(0.0, result.signal(), 0.01);
        assertEquals(0.0, result.strength(), 0.01);
    }

    private Candle createCandle(double price) {
        Candle candle = Candle.of(Instant.parse("2024-01-01T00:00:00Z"), 0, price);
        when(candleIndexProvider.provideIndex(candle)).thenReturn(0L);

        return candle;
    }
}
