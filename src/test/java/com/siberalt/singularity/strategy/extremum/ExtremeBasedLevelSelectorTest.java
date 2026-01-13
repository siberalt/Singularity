package com.siberalt.singularity.strategy.extremum;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.level.Level;
import com.siberalt.singularity.strategy.level.selector.ExtremeBasedLevelSelector;
import com.siberalt.singularity.strategy.level.selector.LevelPair;
import com.siberalt.singularity.strategy.market.CandleIndexProvider;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

class ExtremeBasedLevelSelectorTest {
    @Test
    void selectsClosestLevelsWithinVicinity() {
        ExtremumLocator minimumLocator = Mockito.mock(ExtremumLocator.class);
        ExtremumLocator maximumLocator = Mockito.mock(ExtremumLocator.class);
        CandleIndexProvider candleIndexProvider = Mockito.mock(CandleIndexProvider.class);

        Candle lastMinimum = Candle.of(Instant.parse("2021-01-01T00:00:00Z"),100.0);
        Candle lastMaximum = Candle.of(Instant.parse("2021-01-02T00:00:00Z"),200.0);

        when(minimumLocator.locate(Mockito.anyList(), Mockito.any())).thenReturn(List.of(lastMinimum));
        when(maximumLocator.locate(Mockito.anyList(), Mockito.any())).thenReturn(List.of(lastMaximum));

        Level<Double> resistanceLevel = new Level<>(0, 10, index -> 101.0);
        Level<Double> supportLevel = new Level<>(0, 10, index -> 199.0);

        ExtremeBasedLevelSelector selector = new ExtremeBasedLevelSelector(minimumLocator, maximumLocator);
        selector.setMinimumVicinity(0.02).setMaximumVicinity(0.02);

        List<LevelPair> result = selector.select(
            List.of(resistanceLevel),
            List.of(supportLevel),
            List.of(),
            candleIndexProvider
        );

        assertEquals(1, result.size());
        assertEquals(resistanceLevel, result.get(0).resistance());
        assertEquals(supportLevel, result.get(0).support());
    }

    @Test
    void doesNotSelectLevelsOutsideVicinity() {
        ExtremumLocator minimumLocator = Mockito.mock(ExtremumLocator.class);
        ExtremumLocator maximumLocator = Mockito.mock(ExtremumLocator.class);
        CandleIndexProvider candleIndexProvider = Mockito.mock(CandleIndexProvider.class);

        Candle lastMinimum = Candle.of(Instant.parse("2021-01-01T00:00:00Z"),100.0);
        Candle lastMaximum = Candle.of(Instant.parse("2021-01-02T00:00:00Z"),200.0);

        when(minimumLocator.locate(Mockito.anyList(), Mockito.any())).thenReturn(List.of(lastMinimum));
        when(maximumLocator.locate(Mockito.anyList(), Mockito.any())).thenReturn(List.of(lastMaximum));

        Level<Double> resistanceLevel = new Level<>(0, 10, index -> 110.0);
        Level<Double> supportLevel = new Level<>(0, 10, index -> 180.0);

        ExtremeBasedLevelSelector selector = new ExtremeBasedLevelSelector(minimumLocator, maximumLocator);
        selector.setMinimumVicinity(0.02).setMaximumVicinity(0.02);

        List<LevelPair> result = selector.select(
            List.of(resistanceLevel),
            List.of(supportLevel),
            List.of(),
            candleIndexProvider
        );

        assertEquals(0, result.size());
    }

    @Test
    void handlesEmptyLevelLists() {
        ExtremumLocator minimumLocator = Mockito.mock(ExtremumLocator.class);
        ExtremumLocator maximumLocator = Mockito.mock(ExtremumLocator.class);
        CandleIndexProvider candleIndexProvider = Mockito.mock(CandleIndexProvider.class);

        when(minimumLocator.locate(Mockito.anyList(), Mockito.any())).thenReturn(List.of());
        when(maximumLocator.locate(Mockito.anyList(), Mockito.any())).thenReturn(List.of());

        ExtremeBasedLevelSelector selector = new ExtremeBasedLevelSelector(minimumLocator, maximumLocator);

        List<LevelPair> result = selector.select(
            List.of(),
            List.of(),
            List.of(),
            candleIndexProvider
        );

        assertEquals(0, result.size());
    }
}
