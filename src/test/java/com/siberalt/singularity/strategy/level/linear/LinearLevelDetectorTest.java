package com.siberalt.singularity.strategy.level.linear;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.math.ArithmeticOperations;
import com.siberalt.singularity.math.LinearFunction2D;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LinearLevelDetectorTest {
    private final StrengthCalculator<Double> strengthCalculator = mock(StrengthCalculator.class);

    @Test
    void calculatesSupportLevelsCorrectly() {
        List<Candle> candles = List.of(
            createCandle("2023-01-01T00:00:00Z", 13.22),
            createCandle("2023-01-01T00:01:00Z", 10.20),
            createCandle("2023-01-01T00:02:00Z", 14.75),
            createCandle("2023-01-01T00:03:00Z", 9.95),
            createCandle("2023-01-01T00:04:00Z", 11.75),
            createCandle("2023-01-01T00:05:00Z", 12.75),
            createCandle("2023-01-01T00:06:00Z", 10.10)
        );

        LinearLevelDetector calculator = LinearLevelDetector.createSupport(
            3, 0.1, this::getClosePrice
        );
        calculator.setStrengthCalculator(strengthCalculator);

        // Mock the strength calculation
        // Assuming strength is calculated as the square of the number of candles in the frame
        // For this test, we assume the strength is 3 * 3 = 9
        // This is a simplification; in a real scenario, the strengthCalculator would have more complex logic
        double expectedStrength = 3 * 3; // Example strength value
        when(strengthCalculator.calculate(any())).thenReturn(expectedStrength);
        List<LinearLevel<Double>> levels = calculator.detect(candles);

        assertEquals(1, levels.size());
        LinearLevel<Double> level = levels.get(0);
        LinearFunction2D<Double> expectedFunction = new LinearFunction2D<>(0., 10.2, ArithmeticOperations.DOUBLE);

        assertLevel(
            level,
            Instant.parse("2023-01-01T00:00:00Z"),
            Instant.parse("2023-01-01T00:06:00Z"),
            0,
            6,
            expectedStrength
        );
        assertFunction(level.function(), expectedFunction, 0.5, 0., 4.);
    }

    @Test
    void calculatesSeveralSupportLevels() {
        List<Candle> candles = List.of(
            createCandle("2023-01-01T00:00:00Z", 4),
            createCandle("2023-01-01T00:01:00Z", 5.1),
            createCandle("2023-01-01T00:02:00Z", 5.95),
            createCandle("2023-01-01T00:03:00Z", 7),
            createCandle("2023-01-01T00:04:00Z", 6.95),
            createCandle("2023-01-01T00:05:00Z", 6.99),
            createCandle("2023-01-01T00:06:00Z", 7.05)
        );

        LinearLevelDetector calculator = LinearLevelDetector.createSupport(
            1, 0.1, this::getClosePrice
        );
        calculator.setStrengthCalculator(strengthCalculator);
        // Mock the strength calculation
        // Assuming strength is calculated as the square of the number of candles in the frame
        // For this test, we assume the strength is 3 * 3 = 9
        double expectedStrength1 = 3 * 3; // Example strength value
        double expectedStrength2 = 2 * 3; // Example strength value for the second level
        when(strengthCalculator.calculate(any())).thenReturn(expectedStrength1, expectedStrength2);

        List<LinearLevel<Double>> levels = calculator.detect(candles);

        assertEquals(2, levels.size());
        LinearLevel<Double> level1 = levels.get(0);
        LinearLevel<Double> level2 = levels.get(1);

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
        List<Candle> candles = List.of(createCandle("2023-01-01T00:00:00Z", 13.22));

        LinearLevelDetector calculator = LinearLevelDetector.createSupport(
            2, 0.1, this::getClosePrice
        );
        calculator.setStrengthCalculator(strengthCalculator);
        // Mock the strength calculation
        verify(strengthCalculator, never()).calculate(any());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            calculator.detect(candles)
        );

        assertEquals("Not enough data to calculate support levels", exception.getMessage());
    }

    @Test
    void calculatesResistanceLevelsCorrectly() {
        List<Candle> candles = List.of(
            createCandle("2023-01-01T00:00:00Z", 13.22),
            createCandle("2023-01-01T00:01:00Z", 10.20),
            createCandle("2023-01-01T00:02:00Z", 13.1),
            createCandle("2023-01-01T00:03:00Z", 10.19),
            createCandle("2023-01-01T00:04:00Z", 11.75),
            createCandle("2023-01-01T00:05:00Z", 12.9),
            createCandle("2023-01-01T00:06:00Z", 12.95),
            createCandle("2023-01-01T00:07:00Z", 13.0),
            createCandle("2023-01-01T00:08:00Z", 11.0)
        );

        LinearLevelDetector calculator = LinearLevelDetector.createResistance(3, 0.1, this::getClosePrice);
        calculator.setStrengthCalculator(strengthCalculator);
        // Mock the strength calculation
        // Assuming strength is calculated as the square of the number of candles in the frame
        // For this test, we assume the strength is 3 * 3 = 9
        double expectedStrength = 3 * 3; // Example strength value
        when(strengthCalculator.calculate(any())).thenReturn(expectedStrength);

        List<LinearLevel<Double>> result = calculator.detect(candles);

        LinearLevel<Double> level = result.get(0);
        LinearFunction2D<Double> expectedFunction = new LinearFunction2D<>(0., 13.1, ArithmeticOperations.DOUBLE);

        assertLevel(
            level,
            Instant.parse("2023-01-01T00:00:00Z"),
            Instant.parse("2023-01-01T00:08:00Z"),
            0,
            8,
            expectedStrength
        );
        assertFunction(level.function(), expectedFunction, 0.5, 0., 3.);
    }

    @Test
    void handlesEmptyCandleListGracefully() {
        List<Candle> candles = List.of();

        LinearLevelDetector calculator = LinearLevelDetector.createSupport(
            2, 0.1, this::getClosePrice
        );
        calculator.setStrengthCalculator(strengthCalculator);
        verify(strengthCalculator, never()).calculate(any());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            calculator.detect(candles)
        );

        assertEquals("Not enough data to calculate support levels", exception.getMessage());
    }

    @Test
    void detectCalledMultipleTimesWithFracture() {
        List<Candle> candles1 = List.of(
            createCandle("2023-01-01T00:00:00Z", 10.0),
            createCandle("2023-01-01T00:01:00Z", 12.0),
            createCandle("2023-01-01T00:02:00Z", 11.0),
            createCandle("2023-01-01T00:03:00Z", 13.0),
            createCandle("2023-01-01T00:04:00Z", 14.0)
        );
        List<Candle> candles2 = List.of(
            createCandle("2023-01-01T00:05:00Z", 14.0),
            createCandle("2023-01-01T00:06:00Z", 14.0),
            createCandle("2023-01-01T00:07:00Z", 13.5),
            createCandle("2023-01-01T00:08:00Z", 14.2),
            createCandle("2023-01-01T00:09:00Z", 14.0)
        );

        LinearLevelDetector detector = LinearLevelDetector.createSupport(
            2, 0.1, this::getClosePrice
        );
        StrengthCalculator<Double> strengthCalculator = mock(StrengthCalculator.class);
        detector.setStrengthCalculator(strengthCalculator);
        when(strengthCalculator.calculate(any()))
            .thenReturn(25.0)
            .thenReturn(45.0);

        // First call to detect
        List<LinearLevel<Double>> levelsFirstCall = detector.detect(candles1);
        assertEquals(1, levelsFirstCall.size());

        assertLevel(
            levelsFirstCall.get(0),
            Instant.parse("2023-01-01T00:00:00Z"), // from
            Instant.parse("2023-01-01T00:03:00Z"), // to
            0, // indexFrom
            3, // indexTo
            25.0 // strength
        );

        // Second call to detect with the same data
        List<LinearLevel<Double>> levelsSecondCall = detector.detect(candles2);
        assertEquals(1, levelsSecondCall.size());

        assertLevel(
            levelsSecondCall.get(0),
            Instant.parse("2023-01-01T00:04:00Z"), // from
            Instant.parse("2023-01-01T00:09:00Z"), // to
            4, // indexFrom
            9, // indexTo
            45.0 // strength
        );
    }

    @Test
    void detectCalledMultipleTimesWithoutFracture() {
        List<Candle> candles1 = List.of(
            createCandle("2023-01-01T00:00:00Z", 10.0),
            createCandle("2023-01-01T00:01:00Z", 10.0),
            createCandle("2023-01-01T00:02:00Z", 10.0),
            createCandle("2023-01-01T00:03:00Z", 10.0),
            createCandle("2023-01-01T00:04:00Z", 10.0)
        );
        List<Candle> candles2 = List.of(
            createCandle("2023-01-01T00:05:00Z", 10.0),
            createCandle("2023-01-01T00:06:00Z", 10.0),
            createCandle("2023-01-01T00:07:00Z", 10.0),
            createCandle("2023-01-01T00:08:00Z", 10.0),
            createCandle("2023-01-01T00:09:00Z", 10.0)
        );

        LinearLevelDetector detector = LinearLevelDetector.createSupport(
            2, 0.1, this::getClosePrice
        );
        StrengthCalculator<Double> strengthCalculator = mock(StrengthCalculator.class);
        detector.setStrengthCalculator(strengthCalculator);
        when(strengthCalculator.calculate(any()))
            .thenReturn(25.0)
            .thenReturn(45.0);

        // First call to detect
        List<LinearLevel<Double>> levelsFirstCall = detector.detect(candles1);
        assertEquals(1, levelsFirstCall.size());

        assertLevel(
            levelsFirstCall.get(0),
            Instant.parse("2023-01-01T00:00:00Z"), // from
            Instant.parse("2023-01-01T00:04:00Z"), // to
            0, // indexFrom
            4, // indexTo
            25.0 // strength
        );

        // Second call to detect with continuous data
        List<LinearLevel<Double>> levelsSecondCall = detector.detect(candles2);
        assertEquals(1, levelsSecondCall.size());

        assertLevel(
            levelsSecondCall.get(0),
            Instant.parse("2023-01-01T00:00:00Z"), // from
            Instant.parse("2023-01-01T00:09:00Z"), // to
            0, // indexFrom
            9, // indexTo
            45.0 // strength
        );
    }

    private double getClosePrice(Candle candle) {
        return candle.getClosePrice().toBigDecimal().doubleValue();
    }

    private Candle createCandle(String time, double close) {
        return Candle.of(Instant.parse(time), 0, close);
    }

    private void assertLevel(
        LinearLevel<Double> level,
        Instant from,
        Instant to,
        int indexFrom,
        int indexTo,
        double strength
    ) {
        assertEquals(from, level.getTimeFrom());
        assertEquals(to, level.getTimeTo());
        assertEquals(indexFrom, level.getIndexFrom());
        assertEquals(indexTo, level.getIndexTo());
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