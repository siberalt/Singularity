package com.siberalt.singularity.strategy.level.selector;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.TimePoint;
import com.siberalt.singularity.strategy.level.Level;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class StrongestLevelPairSelectorTest {

    // Простая заглушка Candle
    private Candle candle(long index) {
        return Candle.of(new TimePoint(index), 0.0);
    }

    // Простая заглушка Level
    private Level<Double> level(long from, long to, double strength, double value) {
        return new Level<>(new TimePoint(from), new TimePoint(to), idx -> value, strength);
    }

    @Nested
    @DisplayName("Базовый выбор пар")
    class BasicSelection {

        @Test
        @DisplayName("Должен вернуть топ N пар по временной близости и силе")
        void shouldReturnTopPairsByTimeAndStrength() {
            List<Level<Double>> resistance = List.of(
                level(10, 20, 5.0, 105.0), // to=20
                level(15, 25, 4.0, 110.0)  // to=25
            );
            List<Level<Double>> support = List.of(
                level(10, 22, 4.0, 98.0),  // to=22 → dist=2
                level(12, 18, 3.0, 95.0)   // to=18 → dist=2
            );

            List<Candle> candles = List.of(candle(24)); // currentIndex = 24

            StrongestLevelPairSelector selector = new StrongestLevelPairSelector(2);

            List<LevelPair> result = selector.select(resistance, support, candles);

            assertEquals(2, result.size());

            // Пара 1: resistance(10,20) + support(10,22) → |20-22|=2
            // Пара 2: resistance(10,20) + support(12,18) → |20-18|=2 → одинаковое расстояние → выбираем по силе
            // Сила: 5+4=9 > 5+3=8 → сначала (5+4)
            LevelPair first = result.get(0);
            assertEquals(5.0, first.resistance().strength(), 0.001);
            assertEquals(4.0, first.support().strength(), 0.001);
        }
    }

    @Nested
    @DisplayName("Фильтрация по силе")
    class StrengthFiltering {

        @Test
        @DisplayName("Должен фильтровать уровни по minStrength")
        void shouldFilterByMinStrength() {
            List<Level<Double>> resistance = List.of(
                level(10, 20, 1.0, 105.0),
                level(15, 25, 3.5, 110.0)
            );
            List<Level<Double>> support = List.of(
                level(10, 22, 2.0, 98.0),
                level(10, 22, 3.0, 101.0)
            );

            List<Candle> candles = List.of(candle(24));

            StrongestLevelPairSelector selector = new StrongestLevelPairSelector(2, 3.0);

            List<LevelPair> result = selector.select(resistance, support, candles);

            // Только resistance(15,25) имеет strength >= 3.0
            assertEquals(1, result.size());
            assertEquals(3.5, result.get(0).resistance().strength(), 0.001);
            assertEquals(3.0, result.get(0).support().strength(), 0.001);
        }
    }

    @Nested
    @DisplayName("Ограничение количества")
    class Limiting {

        @Test
        @DisplayName("Должен ограничивать количество пар до maxPairsAmount")
        void shouldLimitToMaxPairsAmount() {
            List<Level<Double>> resistance = List.of(level(10, 20, 5.0, 105.0));
            List<Level<Double>> support = List.of(
                level(10, 22, 4.0, 98.0),
                level(12, 18, 3.0, 95.0),
                level(14, 24, 2.0, 90.0)
            );

            List<Candle> candles = List.of(candle(24));

            StrongestLevelPairSelector selector = new StrongestLevelPairSelector(2);

            List<LevelPair> result = selector.select(resistance, support, candles);

            assertEquals(2, result.size());
        }
    }

    @Nested
    @DisplayName("Временное ограничение")
    class TimeDistanceFiltering {

        @Test
        @DisplayName("Должен фильтровать пары по maxTimeDistance")
        void shouldFilterByMaxTimeDistance() {
            List<Level<Double>> resistance = List.of(level(10, 10, 5.0, 105.0)); // to=10
            List<Level<Double>> support = List.of(
                level(10, 12, 4.0, 98.0), // |10-12|=2 → OK
                level(10, 25, 3.0, 95.0)  // |10-25|=15 → >10 → отфильтрован
            );

            List<Candle> candles = List.of(candle(24));

            StrongestLevelPairSelector selector = new StrongestLevelPairSelector(2, 0.0, 10, 10L, 0.4);

            List<LevelPair> result = selector.select(resistance, support, candles);

            assertEquals(1, result.size());
            assertEquals(4.0, result.get(0).support().strength(), 0.001);
        }

        @Test
        @DisplayName("При maxTimeDistance = null — не фильтрует")
        void shouldNotFilterWhenMaxTimeDistanceIsNull() {
            List<Level<Double>> resistance = List.of(level(10, 10, 5.0, 105.0));
            List<Level<Double>> support = List.of(level(10, 25, 3.0, 95.0));

            List<Candle> candles = List.of(candle(24));

            StrongestLevelPairSelector selector = new StrongestLevelPairSelector(2, 0.0, 10, null, 1.0);

            List<LevelPair> result = selector.select(resistance, support, candles);

            assertEquals(1, result.size());
        }
    }

    @Nested
    @DisplayName("Сортировка")
    class Sorting {

        @Test
        @DisplayName("Сначала сортирует по минимальному временному расстоянию")
        void shouldSortByTimeDistanceFirst() {
            List<Level<Double>> resistance = List.of(level(10, 20, 5.0, 105.0));
            List<Level<Double>> support = List.of(
                level(10, 22, 4.0, 98.0), // |20-22|=2
                level(10, 25, 3.0, 95.0)  // |20-25|=5
            );

            List<Candle> candles = List.of(candle(24));

            StrongestLevelPairSelector selector = new StrongestLevelPairSelector(2);

            List<LevelPair> result = selector.select(resistance, support, candles);

            assertEquals(4.0, result.get(0).support().strength(), 0.001); // сначала меньшее расстояние
        }

        @Test
        @DisplayName("При равном расстоянии — по суммарной силе (по убыванию)")
        void shouldSortByStrengthWhenTimeDistanceIsEqual() {
            List<Level<Double>> resistance = List.of(level(10, 20, 5.0, 105.0));
            List<Level<Double>> support = List.of(
                level(10, 18, 4.0, 98.0), // |20-18|=2, total=9.0
                level(10, 22, 3.0, 95.0)  // |20-22|=2, total=8.0
            );

            List<Candle> candles = List.of(candle(24));

            StrongestLevelPairSelector selector = new StrongestLevelPairSelector(2);

            List<LevelPair> result = selector.select(resistance, support, candles);

            assertEquals(4.0, result.get(0).support().strength(), 0.001); // сила 9.0 > 8.0
        }
    }

    @Nested
    @DisplayName("Граничные случаи")
    class EdgeCases {

        @Test
        @DisplayName("При null списке свечей — возвращает пусто")
        void shouldReturnEmptyOnNullCandles() {
            List<Level<Double>> resistance = List.of(level(10, 20, 5.0, 105.0));
            List<Level<Double>> support = List.of(level(10, 22, 4.0, 98.0));

            StrongestLevelPairSelector selector = new StrongestLevelPairSelector(2);

            List<LevelPair> result = selector.select(resistance, support, null);

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("При пустом списке свечей — возвращает пусто")
        void shouldReturnEmptyOnEmptyCandles() {
            List<Level<Double>> resistance = List.of(level(10, 20, 5.0, 105.0));
            List<Level<Double>> support = List.of(level(10, 22, 4.0, 98.0));

            StrongestLevelPairSelector selector = new StrongestLevelPairSelector(2);

            List<LevelPair> result = selector.select(resistance, support, Collections.emptyList());

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("При пустых уровнях — возвращает пусто")
        void shouldReturnEmptyOnEmptyLevels() {
            List<Candle> candles = List.of(candle(24));
            StrongestLevelPairSelector selector = new StrongestLevelPairSelector(2);

            List<LevelPair> result = selector.select(Collections.emptyList(), Collections.emptyList(), candles);

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("При отсутствии валидных пар — возвращает пусто")
        void shouldReturnEmptyWhenNoValidPairs() {
            List<Level<Double>> resistance = List.of(level(10, 20, 5.0, 100.0));
            List<Level<Double>> support = List.of(level(10, 22, 4.0, 105.0)); // support > resistance → invalid

            List<Candle> candles = List.of(candle(24));
            StrongestLevelPairSelector selector = new StrongestLevelPairSelector(2);

            List<LevelPair> result = selector.select(resistance, support, candles);

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("Конструкторы")
    class Constructors {
        @Test
        @DisplayName("maxPairsAmount <= 0 → исключение")
        void shouldThrowOnInvalidMaxPairsAmount() {
            assertThrows(IllegalArgumentException.class, () -> new StrongestLevelPairSelector(0));
        }
    }
}
