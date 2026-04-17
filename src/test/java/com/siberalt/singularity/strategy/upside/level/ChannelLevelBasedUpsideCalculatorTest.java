package com.siberalt.singularity.strategy.upside.level;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.TimePoint;
import com.siberalt.singularity.strategy.level.Level;
import com.siberalt.singularity.strategy.level.selector.LevelPair;
import com.siberalt.singularity.strategy.upside.Upside;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChannelLevelBasedUpsideCalculatorTest {

    private final ChannelLevelBasedUpsideCalculator calculator = new ChannelLevelBasedUpsideCalculator();

    @Nested
    @DisplayName("Позиция цены у поддержки")
    class AtSupportTests {
        @Test
        @DisplayName("Должен возвращать положительный upside при цене у поддержки")
        void shouldReturnPositiveUpsideAtSupport() {
            LevelPair levelPair = new LevelPair(
                new Level<>(new TimePoint(1), new TimePoint(1),  t -> 110.0, 1),
                new Level<>(new TimePoint(1), new TimePoint(1),  t -> 100.0, 1)
            );
            Candle candle = Candle.of(new TimePoint(1), 100);
            Upside result = calculator.calculate(levelPair, List.of(candle));

            assertTrue(result.signal() > 0, "Upside at support should be positive");
            assertEquals(1.0, result.signal(), 0.001);
        }
    }

    @Nested
    @DisplayName("Позиция цены у сопротивления")
    class AtResistanceTests {
        @Test
        @DisplayName("Должен возвращать отрицательный upside при цене у сопротивления")
        void shouldReturnNegativeUpsideAtResistance() {
            LevelPair levelPair = new LevelPair(
                new Level<>(new TimePoint(1), new TimePoint(1),  t -> 110.0, 1),
                new Level<>(new TimePoint(1), new TimePoint(1),  t -> 100.0, 1)
            );
            Candle candle = Candle.of(new TimePoint(1), 110);
            Upside result = calculator.calculate(levelPair, List.of(candle));

            assertTrue(result.signal() < 0, "Upside at resistance should be negative");
            assertEquals(-1.0, result.signal(), 0.01);
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
            Upside result = calculator.calculate(levelPair, List.of(candle));
            System.out.print(result);

            // Цена 105 — раньше была бы выше центра, но теперь центр выше → меньше upside
            assertTrue(result.signal() < 0, "Upside should be lower due end stronger resistance");
        }

        @Test
        @DisplayName("При более сильной поддержке нейтральная точка смещается ниже")
        void shouldShiftNeutralPointDownWhenSupportIsStronger() {
            LevelPair levelPair = new LevelPair(
                new Level<>(new TimePoint(1), new TimePoint(1),  t -> 110.0, 1),
                new Level<>(new TimePoint(1), new TimePoint(1),  t -> 100.0, 2)
            );
            Candle candle = Candle.of(new TimePoint(1), 105);
            Upside result = calculator.calculate(levelPair, List.of(candle));
            System.out.print(result);

            // Цена 105 — раньше была бы выше центра, но теперь центр выше → меньше upside
            assertTrue(result.signal() > 0, "Upside should be bigger due end stronger support");
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
            Upside result = calculator.calculate(levelPair, List.of(candle));

            assertEquals(Upside.NEUTRAL, result);
        }

        @Test
        @DisplayName("Должен вернуть NEUTRAL при нулевой ширине канала")
        void shouldReturnNeutralOnZeroChannelWidth() {
            Level<Double> level = new Level<>(new TimePoint(1), new TimePoint(1), t -> 100.0, 1);
            LevelPair levelPair = new LevelPair(level, level);
            Candle candle = Candle.of(new TimePoint(1), 106);

            Upside result = calculator.calculate(levelPair, List.of(candle));

            assertEquals(Upside.NEUTRAL, result);
        }

        @Test
        @DisplayName("Должен использовать fallback при нулевой сумме сил")
        void shouldUseFallbackStrengthsWhenSumIsZero() {
            LevelPair levelPair = new LevelPair(
                new Level<>(new TimePoint(1), new TimePoint(1),  t -> 110.0, 0),
                new Level<>(new TimePoint(1), new TimePoint(1),  t -> 100.0, 0)
            );
            Candle candle = Candle.of(new TimePoint(1), 105);

            Upside result = calculator.calculate(levelPair, List.of(candle));

            // Должно работать без ошибки деления на ноль
            assertNotNull(result);
        }
    }
}
