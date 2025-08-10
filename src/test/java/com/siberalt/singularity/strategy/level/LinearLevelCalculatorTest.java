package com.siberalt.singularity.strategy.level;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.ReadCandleRepository;
import com.siberalt.singularity.math.ArithmeticOperations;
import com.siberalt.singularity.math.LinearFunction2D;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LinearLevelCalculatorTest {
    private final ReadCandleRepository candleRepository = mock(ReadCandleRepository.class);

    private final StrengthCalculator<Double> strengthCalculator = mock(StrengthCalculator.class);

    @Test
    void calculatesSupportLevelsCorrectly() {
        mockCandleRepository(List.of(
            createCandle("2023-01-01T00:00:00Z", 13.22),
            createCandle("2023-01-01T00:01:00Z", 10.20),
            createCandle("2023-01-01T00:02:00Z", 14.75),
            createCandle("2023-01-01T00:03:00Z", 9.95),
            createCandle("2023-01-01T00:04:00Z", 11.75),
            createCandle("2023-01-01T00:05:00Z", 12.75),
            createCandle("2023-01-01T00:06:00Z", 10.10)
        ));

        LinearLevelCalculator calculator = LinearLevelCalculator.createSupport(
            candleRepository, 3, 0.1, this::getClosePrice
        );
        calculator.setStrengthCalculator(strengthCalculator);

        // Mock the strength calculation
        // Assuming strength is calculated as the square of the number of candles in the frame
        // For this test, we assume the strength is 3 * 3 = 9
        // This is a simplification; in a real scenario, the strengthCalculator would have more complex logic
        double expectedStrength = 3 * 3; // Example strength value
        when(strengthCalculator.calculate(any())).thenReturn(expectedStrength);
        Result<Double> result = calculator.calculate(
            "instrument1", Instant.parse("2023-01-01T00:00:00Z"), Instant.parse("2023-01-01T00:06:00Z")
        );

        assertEquals(1, result.levels().size());
        Result.Level<Double> level = result.levels().get(0);
        LinearFunction2D<Double> expectedFunction = new LinearFunction2D<>(0., 10.2, ArithmeticOperations.DOUBLE);

        assertLevel(level, Instant.parse("2023-01-01T00:00:00Z"), Instant.parse("2023-01-01T00:06:00Z"), 0, 6, expectedStrength);
        assertFunction(level.function(), expectedFunction, 0.5, 0., 4.);
    }

    @Test
    void calculatesSeveralSupportLevels() {
        mockCandleRepository(List.of(
            createCandle("2023-01-01T00:00:00Z", 4),
            createCandle("2023-01-01T00:01:00Z", 5.1),
            createCandle("2023-01-01T00:02:00Z", 5.95),
            createCandle("2023-01-01T00:03:00Z", 7),
            createCandle("2023-01-01T00:04:00Z", 6.95),
            createCandle("2023-01-01T00:05:00Z", 6.99),
            createCandle("2023-01-01T00:06:00Z", 7.05)
        ));

        LinearLevelCalculator calculator = LinearLevelCalculator.createSupport(
            candleRepository, 1, 0.1, this::getClosePrice
        );
        calculator.setStrengthCalculator(strengthCalculator);
        // Mock the strength calculation
        // Assuming strength is calculated as the square of the number of candles in the frame
        // For this test, we assume the strength is 3 * 3 = 9
        double expectedStrength1 = 3 * 3; // Example strength value
        double expectedStrength2 = 2 * 3; // Example strength value for the second level
        when(strengthCalculator.calculate(any())).thenReturn(expectedStrength1, expectedStrength2);

        Result<Double> result = calculator.calculate(
            "instrument1", Instant.parse("2023-01-01T00:00:00Z"), Instant.parse("2023-01-01T00:06:00Z")
        );

        assertEquals(2, result.levels().size());
        Result.Level<Double> level1 = result.levels().get(0);
        Result.Level<Double> level2 = result.levels().get(1);

        LinearFunction2D<Double> expectedFunction1 = new LinearFunction2D<>(
            1., 4., ArithmeticOperations.DOUBLE
        );
        LinearFunction2D<Double> expectedFunction2 = new LinearFunction2D<>(
            0., 7., ArithmeticOperations.DOUBLE
        );
        assertLevel(
            level1, Instant.parse("2023-01-01T00:00:00Z"), Instant.parse("2023-01-01T00:03:00Z"), 0, 3, expectedStrength1
        );
        assertFunction(level1.function(), expectedFunction1, 0.5, 0., 1., 2., 3.);

        assertLevel(
            level2, Instant.parse("2023-01-01T00:04:00Z"), Instant.parse("2023-01-01T00:06:00Z"), 4, 6, expectedStrength2
        );
        assertFunction(level2.function(), expectedFunction2, 0.5, 3., 4., 5., 6.);
    }

    @Test
    void throwsExceptionWhenNotEnoughCandles() {
        mockCandleRepository(List.of(createCandle("2023-01-01T00:00:00Z", 13.22)));

        LinearLevelCalculator calculator = LinearLevelCalculator.createSupport(
            candleRepository, 2, 0.1, this::getClosePrice
        );
        calculator.setStrengthCalculator(strengthCalculator);
        // Mock the strength calculation
        verify(strengthCalculator, never()).calculate(any());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            calculator.calculate("instrument1", Instant.parse("2023-01-01T00:00:00Z"), Instant.parse("2023-01-01T01:00:00Z"))
        );

        assertEquals("Not enough data to calculate support levels", exception.getMessage());
    }

    @Test
    void calculatesResistanceLevelsCorrectly() {
        mockCandleRepository(List.of(
            createCandle("2023-01-01T00:00:00Z", 13.22),
            createCandle("2023-01-01T00:01:00Z", 10.20),
            createCandle("2023-01-01T00:02:00Z", 13.1),
            createCandle("2023-01-01T00:03:00Z", 10.19),
            createCandle("2023-01-01T00:04:00Z", 11.75),
            createCandle("2023-01-01T00:05:00Z", 12.9),
            createCandle("2023-01-01T00:06:00Z", 12.95),
            createCandle("2023-01-01T00:07:00Z", 13.0),
            createCandle("2023-01-01T00:08:00Z", 11.0)
        ));

        LinearLevelCalculator calculator = LinearLevelCalculator.createResistance(
            candleRepository, 3, 0.1, this::getClosePrice
        );
        calculator.setStrengthCalculator(strengthCalculator);
        // Mock the strength calculation
        // Assuming strength is calculated as the square of the number of candles in the frame
        // For this test, we assume the strength is 3 * 3 = 9
        double expectedStrength = 3 * 3; // Example strength value
        when(strengthCalculator.calculate(any())).thenReturn(expectedStrength);

        Result<Double> result = calculator.calculate(
            "instrument1", Instant.parse("2023-01-01T00:00:00Z"), Instant.parse("2023-01-01T00:08:00Z")
        );

        Result.Level<Double> level = result.levels().get(0);
        LinearFunction2D<Double> expectedFunction = new LinearFunction2D<>(0., 13.1, ArithmeticOperations.DOUBLE);

        assertLevel(level, Instant.parse("2023-01-01T00:00:00Z"), Instant.parse("2023-01-01T00:08:00Z"), 0, 8, expectedStrength);
        assertFunction(level.function(), expectedFunction, 0.5, 0., 3.);
    }

    @Test
    void handlesEmptyCandleListGracefully() {
        mockCandleRepository(
            List.of(),
            Instant.parse("2023-01-01T00:00:00Z"),
            Instant.parse("2023-01-01T01:00:00Z")
        );

        LinearLevelCalculator calculator = LinearLevelCalculator.createSupport(
            candleRepository, 2, 0.1, this::getClosePrice
        );
        calculator.setStrengthCalculator(strengthCalculator);
        verify(strengthCalculator, never()).calculate(any());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            calculator.calculate(
                "instrument1",
                Instant.parse("2023-01-01T00:00:00Z"),
                Instant.parse("2023-01-01T01:00:00Z")
            )
        );

        assertEquals("Not enough data to calculate support levels", exception.getMessage());
    }

    private void mockCandleRepository(List<Candle> candles, Instant fromTime, Instant toTime) {
        when(candleRepository.getPeriod("instrument1", fromTime, toTime)).thenReturn(candles);
    }

    private void mockCandleRepository(List<Candle> candles) {
        Instant from = candles.get(0).getTime();
        Instant to = candles.get(candles.size() - 1).getTime();
        mockCandleRepository(candles, from, to);
    }

    private double getClosePrice(Candle candle) {
        return candle.getClosePrice().toBigDecimal().doubleValue();
    }

    private Candle createCandle(String time, double close) {
        return Candle.of(Instant.parse(time), 0, close);
    }

    private void assertLevel(
        Result.Level<Double> level,
        Instant from,
        Instant to,
        int indexFrom,
        int indexTo,
        double strength
    ) {
        assertEquals(from, level.from());
        assertEquals(to, level.to());
        assertEquals(indexFrom, level.indexFrom());
        assertEquals(indexTo, level.indexTo());
        assertEquals(strength, level.strength());
    }

    private void assertFunction(
        LinearFunction2D<Double> function,
        LinearFunction2D<Double> expectedFunction,
        double delta,
        double... xValues
    ) {
        assertNotNull(function);
        for (double x : xValues) {
            assertEquals(expectedFunction.calculate(x), function.calculate(x), delta);
        }
    }
}