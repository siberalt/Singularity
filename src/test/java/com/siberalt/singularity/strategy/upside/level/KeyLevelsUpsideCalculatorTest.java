package com.siberalt.singularity.strategy.upside.level;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.level.Level;
import com.siberalt.singularity.strategy.level.LevelDetector;
import com.siberalt.singularity.strategy.level.selector.LevelPair;
import com.siberalt.singularity.strategy.level.selector.LevelSelector;
import com.siberalt.singularity.strategy.upside.Upside;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

public class KeyLevelsUpsideCalculatorTest {
    @Test
    void calculatesUpsideWhenSupportAndResistanceAreDetectedWithDifferentValues() {
        Level<Double> support = new Level<>(null, null, 20L, 20L, x -> 60.0, 0.0);
        Level<Double> resistance = new Level<>(null, null, 25L, 25L, x -> 120.0, 0.0);
        Candle lastCandle = createCandle(90.0);

        LevelDetector supportDetector = mock(LevelDetector.class);
        LevelDetector resistanceDetector = mock(LevelDetector.class);
        LevelBasedUpsideCalculator upsideCalculator = mock(LevelBasedUpsideCalculator.class);
        LevelSelector levelSelector = mock(LevelSelector.class);

        when(levelSelector.select(any(), any(), any()))
            .thenReturn(List.of(new LevelPair(resistance, support)));
        doReturn(List.of(support)).when(supportDetector).detect(anyList());
        doReturn(List.of(resistance)).when(resistanceDetector).detect(anyList());
        when(upsideCalculator.calculate(resistance, support, List.of(lastCandle)))
            .thenReturn(new Upside(0.7, 1.5));

        KeyLevelsUpsideCalculator calculator = new KeyLevelsUpsideCalculator(
            supportDetector,
            resistanceDetector,
            upsideCalculator
        );
        calculator.setLevelSelector(levelSelector);
        Upside result = calculator.calculate(List.of(lastCandle));

        assertNotNull(result);
        assertEquals(0.7, result.signal(), 0.01);
        assertEquals(1.5, result.strength(), 0.01);
    }
    @Test
    void returnsZeroUpsideWhenNoSupportOrResistanceIsDetected() {
        Candle lastCandle = Candle.of(Instant.now(), 1000L, 75);

        LevelDetector supportDetector = mock(LevelDetector.class);
        LevelDetector resistanceDetector = mock(LevelDetector.class);
        LevelBasedUpsideCalculator upsideCalculator = mock(LevelBasedUpsideCalculator.class);

        when(supportDetector.detect(anyList())).thenReturn(List.of());
        when(resistanceDetector.detect(anyList())).thenReturn(List.of());

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
        return Candle.of(Instant.parse("2024-01-01T00:00:00Z"), 0, price);
    }
}
