package com.siberalt.singularity.strategy.level.selector;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.level.Level;
import com.siberalt.singularity.strategy.market.CandleIndexProvider;
import com.siberalt.singularity.strategy.market.DefaultCandleIndexProvider;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

public class BasicLevelSelectorTest {

    @Test
    void selectsClosestLevelsBasedOnCurrentPrice() {
        Level<Double> resistance1 = new Level<>(5, 10, price -> 110.0);
        Level<Double> resistance2 = new Level<>(0, 5, price -> 120.0);
        Level<Double> support1 = new Level<>(5, 10, price -> 90.0);
        Level<Double> support2 = new Level<>(5, 10, price -> 80.0);
        Candle recentCandle = Candle.of(null, 0L, 100.0);
        CandleIndexProvider candleIndexProvider = new DefaultCandleIndexProvider();

        BasicLevelSelector selector = new BasicLevelSelector(1);

        List<LevelPair> result = selector.select(
            List.of(resistance1, resistance2),
            List.of(support1, support2),
            List.of(recentCandle),
            candleIndexProvider
        );

        assertEquals(1, result.size());
        assertEquals(resistance1, result.get(0).resistance());
        assertEquals(support1, result.get(0).support());
    }

    @Test
    void handlesEmptyResistanceAndSupportLevels() {
        Candle recentCandle = Candle.of(null, 0L, 100.0);
        CandleIndexProvider candleIndexProvider = mock(CandleIndexProvider.class);

        BasicLevelSelector selector = new BasicLevelSelector(1);

        List<LevelPair> result = selector.select(
            List.of(),
            List.of(),
            List.of(recentCandle),
            candleIndexProvider
        );

        assertEquals(0, result.size());
    }

    @Test
    void limitsNumberOfSelectedLevels() {
        Level<Double> resistance1 = new Level<>(5, 10, price -> 110.0);
        Level<Double> resistance2 = new Level<>(0, 5, price -> 120.0);
        Level<Double> support1 = new Level<>(5, 10, price -> 90.0);
        Level<Double> support2 = new Level<>(5, 10, price -> 80.0);
        Candle recentCandle = Candle.of(null, 0L, 100.0);
        CandleIndexProvider candleIndexProvider = new DefaultCandleIndexProvider();

        BasicLevelSelector selector = new BasicLevelSelector(1);

        List<LevelPair> result = selector.select(
            List.of(resistance1, resistance2),
            List.of(support1, support2),
            List.of(recentCandle),
            candleIndexProvider
        );

        assertEquals(1, result.size());
    }

    @Test
    void handlesSupportGreaterThanResistance() {
        Level<Double> resistance = new Level<>(0, 10, price -> 90.0);
        Level<Double> support = new Level<>(0, 10, price -> 100.0);
        Candle recentCandle = Candle.of(null, 0L, 95.0);
        CandleIndexProvider candleIndexProvider = new DefaultCandleIndexProvider();

        BasicLevelSelector selector = new BasicLevelSelector(1);

        List<LevelPair> result = selector.select(
            List.of(resistance),
            List.of(support),
            List.of(recentCandle),
            candleIndexProvider
        );

        assertEquals(0, result.size());
    }
}
