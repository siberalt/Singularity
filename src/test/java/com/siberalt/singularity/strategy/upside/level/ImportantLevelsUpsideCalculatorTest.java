package com.siberalt.singularity.strategy.upside.level;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.level.Level;
import com.siberalt.singularity.strategy.level.LevelDetector;
import com.siberalt.singularity.strategy.upside.Upside;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

public class ImportantLevelsUpsideCalculatorTest {
    @Test
    void calculatesUpsideWhenSupportAndResistanceAreDetectedWithDifferentValues() {
        Level<Double> support = mock(Level.class);
        Level<Double> resistance = mock(Level.class);
        Candle lastCandle = Candle.of(Instant.now(), 1000L, 90.0);

        when(support.getIndexTo()).thenReturn(20L);
        when(resistance.getIndexTo()).thenReturn(25L);
        when(support.getFunction()).thenReturn(x -> 60.0);
        when(resistance.getFunction()).thenReturn(x -> 120.0);

        LevelDetector<Double> supportDetector = mock(LevelDetector.class);
        LevelDetector<Double> resistanceDetector = mock(LevelDetector.class);
        LevelBasedUpsideCalculator upsideCalculator = mock(LevelBasedUpsideCalculator.class);

        doReturn(List.of(support)).when(supportDetector).detect(anyList());
        doReturn(List.of(resistance)).when(resistanceDetector).detect(anyList());
        when(upsideCalculator.calculate(90.0, resistance, support))
            .thenReturn(new Upside(0.7, 1.5));

        ImportantLevelsUpsideCalculator calculator = new ImportantLevelsUpsideCalculator(supportDetector, resistanceDetector, upsideCalculator);
        Upside result = calculator.calculate("instrument", Instant.now(), List.of(lastCandle));

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

        when(supportDetector.detect(anyList())).thenReturn(List.of());
        when(resistanceDetector.detect(anyList())).thenReturn(List.of());

        ImportantLevelsUpsideCalculator calculator = new ImportantLevelsUpsideCalculator(supportDetector, resistanceDetector, upsideCalculator);
        Upside result = calculator.calculate("instrument", Instant.now(), List.of(lastCandle));

        assertNotNull(result);
        assertEquals(0.0, result.signal(), 0.01);
        assertEquals(0.0, result.strength(), 0.01);
    }
}
