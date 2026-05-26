package com.siberalt.singularity.strategy.extreme;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.level.Level;
import com.siberalt.singularity.strategy.level.selector.ExtremeBasedLevelPairSelector;
import com.siberalt.singularity.strategy.level.selector.LevelPair;
import com.siberalt.singularity.strategy.volatility.VolatilityCalculator;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

class ExtremeBasedLevelSelectorTest {
    @Test
    void selectsClosestLevelsWithinVicinity() {
        ExtremeLocator minimumLocator = Mockito.mock(ExtremeLocator.class);
        ExtremeLocator maximumLocator = Mockito.mock(ExtremeLocator.class);
        VolatilityCalculator volCalcMock = Mockito.mock(VolatilityCalculator.class);

        Candle lastMinimum = Candle.of(Instant.parse("2021-01-01T00:00:00Z"),100.0);
        Candle lastMaximum = Candle.of(Instant.parse("2021-01-02T00:00:00Z"),200.0);

        when(minimumLocator.locate(Mockito.anyList())).thenReturn(List.of(lastMinimum));
        when(maximumLocator.locate(Mockito.anyList())).thenReturn(List.of(lastMaximum));
        when(volCalcMock.calculate(Mockito.anyList())).thenReturn(1.);

        Level<Double> resistanceLevel = new Level<>(0, 10, index -> 199.0);
        Level<Double> supportLevel = new Level<>(0, 10, index -> 101.0);

        ExtremeBasedLevelPairSelector selector = new ExtremeBasedLevelPairSelector(minimumLocator, maximumLocator, volCalcMock);
        selector.setVicinityMultiplier(1);

        List<LevelPair> result = selector.select(
            List.of(resistanceLevel),
            List.of(supportLevel),
            List.of(
                lastMaximum,
                lastMinimum
            )
        );

        assertEquals(1, result.size());
        assertEquals(resistanceLevel, result.get(0).resistance());
        assertEquals(supportLevel, result.get(0).support());
    }

    @Test
    void doesNotSelectLevelsOutsideVicinity() {
        ExtremeLocator minimumLocator = Mockito.mock(ExtremeLocator.class);
        ExtremeLocator maximumLocator = Mockito.mock(ExtremeLocator.class);

        Candle lastMinimum = Candle.of(Instant.parse("2021-01-01T00:00:00Z"),100.0);
        Candle lastMaximum = Candle.of(Instant.parse("2021-01-02T00:00:00Z"),200.0);

        when(minimumLocator.locate(Mockito.anyList())).thenReturn(List.of(lastMinimum));
        when(maximumLocator.locate(Mockito.anyList())).thenReturn(List.of(lastMaximum));

        Level<Double> resistanceLevel = new Level<>(0, 10, index -> 110.0);
        Level<Double> supportLevel = new Level<>(0, 10, index -> 180.0);

        ExtremeBasedLevelPairSelector selector = new ExtremeBasedLevelPairSelector(minimumLocator, maximumLocator);
        selector.setVicinityMultiplier(0.02);

        List<LevelPair> result = selector.select(List.of(resistanceLevel), List.of(supportLevel), List.of());

        assertEquals(0, result.size());
    }

    @Test
    void handlesEmptyLevelLists() {
        ExtremeLocator minimumLocator = Mockito.mock(ExtremeLocator.class);
        ExtremeLocator maximumLocator = Mockito.mock(ExtremeLocator.class);

        when(minimumLocator.locate(Mockito.anyList())).thenReturn(List.of());
        when(maximumLocator.locate(Mockito.anyList())).thenReturn(List.of());

        ExtremeBasedLevelPairSelector selector = new ExtremeBasedLevelPairSelector(minimumLocator, maximumLocator);

        List<LevelPair> result = selector.select(List.of(), List.of(), List.of());

        assertEquals(0, result.size());
    }
}
