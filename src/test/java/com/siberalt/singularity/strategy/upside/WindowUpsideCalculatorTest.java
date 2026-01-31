package com.siberalt.singularity.strategy.upside;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.CandleFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

public class WindowUpsideCalculatorTest {
    private UpsideCalculator baseCalculatorMock;
    private WindowUpsideCalculator calculator;
    private final CandleFactory candleFactory = new CandleFactory("TEST");

    @BeforeEach
    void setUp() {
        baseCalculatorMock = mock(UpsideCalculator.class);
        calculator = new WindowUpsideCalculator(baseCalculatorMock, 3);
    }

    @Test
    void calculatesUpsideWithInitialWindow() {
        List<Candle> candles = List.of(
            candleFactory.createCommon("2024-01-01T00:00:00Z", 100),
            candleFactory.createCommon("2024-01-01T01:00:00Z", 101),
            candleFactory.createCommon("2024-01-01T02:00:00Z", 102)
        );
        when(baseCalculatorMock.calculate(anyList())).thenReturn(Upside.NEUTRAL);

        Upside result = calculator.calculate(candles);

        assertNotNull(result);
        verify(baseCalculatorMock).calculate(candles);
    }

    @Test
    void initializesWindowWithCustomFunction() {
        List<Candle> initCandles = List.of(
            candleFactory.createCommon("2024-01-01T00:00:00Z", 100),
            candleFactory.createCommon("2024-01-01T01:00:00Z", 101),
            candleFactory.createCommon("2024-01-01T02:00:00Z", 102)
        );

        Function<List<Candle>, List<Candle>> customInitFunction = mock(Function.class);
        when(customInitFunction.apply(anyList())).thenReturn(initCandles);

        calculator = new WindowUpsideCalculator(baseCalculatorMock, 3, customInitFunction);

        List<Candle> newCandles = List.of(
            candleFactory.createCommon("2024-01-01T03:00:00Z", 103),
            candleFactory.createCommon("2024-01-01T04:00:00Z", 103)
        );

        List<Candle> expectedWindow = List.of(
            candleFactory.createCommon("2024-01-01T02:00:00Z", 102),
            candleFactory.createCommon("2024-01-01T03:00:00Z", 103),
            candleFactory.createCommon("2024-01-01T04:00:00Z", 103)
        );
        when(baseCalculatorMock.calculate(anyList())).thenReturn(Upside.NEUTRAL);

        calculator.calculate(newCandles);

        verify(baseCalculatorMock).calculate(expectedWindow);
        verify(customInitFunction, times(1)).apply(anyList());
    }

    @Test
    void addsNewCandlesAndRemovesExcessWhenWindowExceedsSize() {
        List<Candle> initialCandles = List.of(
            candleFactory.createCommon("2024-01-01T00:00:00Z", 100),
            candleFactory.createCommon("2024-01-01T01:00:00Z", 101),
            candleFactory.createCommon("2024-01-01T02:00:00Z", 102)
        );
        List<Candle> newCandles = List.of(
            candleFactory.createCommon("2024-01-01T03:00:00Z", 102),
            candleFactory.createCommon("2024-01-01T04:00:00Z", 103)
        );
        List<Candle> expectedWindow1 = List.of(
            candleFactory.createCommon("2024-01-01T00:00:00Z", 100),
            candleFactory.createCommon("2024-01-01T01:00:00Z", 101),
            candleFactory.createCommon("2024-01-01T02:00:00Z", 102)
        );
        List<Candle> expectedWindow2 = List.of(
            candleFactory.createCommon("2024-01-01T02:00:00Z", 102),
            candleFactory.createCommon("2024-01-01T03:00:00Z", 102),
            candleFactory.createCommon("2024-01-01T04:00:00Z", 103)
        );
        when(baseCalculatorMock.calculate(anyList())).thenReturn(Upside.NEUTRAL);

        calculator.calculate(initialCandles);
        calculator.calculate(newCandles);

        verify(baseCalculatorMock).calculate(expectedWindow1);
        verify(baseCalculatorMock).calculate(expectedWindow2);
    }

    @Test
    void handlesEmptyInputWithoutErrors() {
        List<Candle> emptyCandles = List.of();
        when(baseCalculatorMock.calculate(anyList())).thenReturn(Upside.NEUTRAL);

        Upside result = calculator.calculate(emptyCandles);

        assertNotNull(result);
        verify(baseCalculatorMock).calculate(emptyCandles);
    }

    @Test
    void throwsExceptionWhenWindowSizeIsLessThanOne() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            new WindowUpsideCalculator(baseCalculatorMock, 0)
        );

        assertEquals("Window size must be at least 1", exception.getMessage());
    }

    @Test
    void throwsExceptionWhenBaseCalculatorIsNull() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            new WindowUpsideCalculator(null, 3)
        );

        assertEquals("Base calculator cannot be null", exception.getMessage());
    }
}
