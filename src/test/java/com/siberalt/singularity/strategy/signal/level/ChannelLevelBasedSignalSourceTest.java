package com.siberalt.singularity.strategy.signal.level;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.TimePoint;
import com.siberalt.singularity.strategy.level.Level;
import com.siberalt.singularity.strategy.level.selector.LevelPair;
import com.siberalt.singularity.strategy.signal.Signal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChannelLevelBasedSignalSourceTest {

    private final ChannelLevelBasedSignalSource calculator = new ChannelLevelBasedSignalSource();

    @Nested
    @DisplayName("Позиция цены у поддержки")
    class AtSupportTests {
        @Test
        @DisplayName("Должен возвращать положительный signal при цене у поддержки")
        void shouldReturnPositiveSignalAtSupport() {
            LevelPair levelPair = new LevelPair(
                new Level<>(new TimePoint(1), new TimePoint(1),  t -> 110.0, 1),
                new Level<>(new TimePoint(1), new TimePoint(1),  t -> 100.0, 1)
            );
            Candle candle = Candle.of(new TimePoint(1), 100);
            Signal result = calculator.calculate(levelPair, List.of(candle));

            assertTrue(result.confidence() > 0, "Signal at support should be positive");
            assertEquals(1.0, result.confidence(), 0.001);
        }
    }

    @Nested
    @DisplayName("Позиция цены у сопротивления")
    class AtResistanceTests {
        @Test
        @DisplayName("Должен возвращать отрицательный signal при цене у сопротивления")
        void shouldReturnNegativeSignalAtResistance() {
            LevelPair levelPair = new LevelPair(
                new Level<>(new TimePoint(1), new TimePoint(1),  t -> 110.0, 1),
                new Level<>(new TimePoint(1), new TimePoint(1),  t -> 100.0, 1)
            );
            Candle candle = Candle.of(new TimePoint(1), 110);
            Signal result = calculator.calculate(levelPair, List.of(candle));

            assertTrue(result.confidence() < 0, "Signal at resistance should be negative");
            assertEquals(-1.0, result.confidence(), 0.01);
        }
    }

    @Nested
    @DisplayName("Смещение нейтральной точки")
    class AdjustedNeutralPointTests {

        @Test
        @DisplayName("При более сильном сопротивлении нейтральная точка смещается выше")
        void shouldShiftNeutralPointUpWhenResistanceIsStronger() {
            LevelPair levelPair = new LevelPair(
                new Level<>(new TimePoint(1), new TimePoint(1),  t -> 110.0, 2),
                new Level<>(new TimePoint(1), new TimePoint(1),  t -> 100.0, 1)
            );
            Candle candle = Candle.of(new TimePoint(1), 105);
            Signal result = calculator.calculate(levelPair, List.of(candle));
            System.out.print(result);

            // Цена 105 — раньше была бы выше центра, но теперь центр выше → меньше signal
            assertTrue(result.confidence() < 0, "Signal should be lower due end stronger resistance");
        }

        @Test
        @DisplayName("При более сильной поддержке нейтральная точка смещается ниже")
        void shouldShiftNeutralPointDownWhenSupportIsStronger() {
            LevelPair levelPair = new LevelPair(
                new Level<>(new TimePoint(1), new TimePoint(1),  t -> 110.0, 1),
                new Level<>(new TimePoint(1), new TimePoint(1),  t -> 100.0, 2)
            );
            Candle candle = Candle.of(new TimePoint(1), 105);
            Signal result = calculator.calculate(levelPair, List.of(candle));
            System.out.print(result);

            // Цена 105 — раньше была бы выше центра, но теперь центр выше → меньше signal
            assertTrue(result.confidence() > 0, "Signal should be bigger due end stronger support");
        }
    }

    @Nested
    @DisplayName("Граничные случаи")
    class EdgeCases {
        @Test
        @DisplayName("Должен выбросить исключение при пустом списке свечей")
        void shouldThrowOnEmptyCandles() {
            LevelPair levelPair = new LevelPair(
                new Level<>(new TimePoint(1), new TimePoint(1),  t -> 110.0, 2),
                new Level<>(new TimePoint(1), new TimePoint(1),  t -> 100.0, 1)
            );
            assertThrows(IllegalArgumentException.class, () ->
                calculator.calculate(levelPair, List.of())
            );
        }

        @Test
        @DisplayName("Должен вернуть NEUTRAL при выходе за границы канала")
        void shouldReturnNeutralWhenPriceOutsideChannel() {
            LevelPair levelPair = new LevelPair(
                new Level<>(new TimePoint(1), new TimePoint(1),  t -> 105.0, 2),
                new Level<>(new TimePoint(1), new TimePoint(1),  t -> 100.0, 1)
            );
            Candle candle = Candle.of(new TimePoint(1), 106); // Выходит правее верхней линии резистанса
            Signal result = calculator.calculate(levelPair, List.of(candle));

            assertEquals(Signal.NEUTRAL, result);
        }

        @Test
        @DisplayName("Должен вернуть NEUTRAL при нулевой ширине канала")
        void shouldReturnNeutralOnZeroChannelWidth() {
            Level<Double> level = new Level<>(new TimePoint(1), new TimePoint(1), t -> 100.0, 1);
            LevelPair levelPair = new LevelPair(level, level);
            Candle candle = Candle.of(new TimePoint(1), 106);

            Signal result = calculator.calculate(levelPair, List.of(candle));

            assertEquals(Signal.NEUTRAL, result);
        }

        @Test
        @DisplayName("Должен использовать fallback при нулевой сумме сил")
        void shouldUseFallbackStrengthsWhenSumIsZero() {
            LevelPair levelPair = new LevelPair(
                new Level<>(new TimePoint(1), new TimePoint(1),  t -> 110.0, 0),
                new Level<>(new TimePoint(1), new TimePoint(1),  t -> 100.0, 0)
            );
            Candle candle = Candle.of(new TimePoint(1), 105);

            Signal result = calculator.calculate(levelPair, List.of(candle));

            // Должно работать без ошибки деления на ноль
            assertNotNull(result);
        }
    }
}
