package com.siberalt.singularity.strategy.upside.subrange;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.CandleFactory;
import com.siberalt.singularity.strategy.upside.Upside;
import com.siberalt.singularity.strategy.upside.UpsideCalculator;
import org.junit.jupiter.api.Test;

import java.time.Period;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class CalendarPeriodFilterDecoratorTest {

    private final ZoneId zone = ZoneOffset.UTC; // используем не UTC для проверки зоны

    /**
     * Проверяем, что фильтр оставляет только последние 3 дня
     */
    @Test
    void should_FilterOnlyLast2Days() {
        CandleFactory candleFactory = new CandleFactory("TEST");
        List<Candle> candles = List.of(
            candleFactory.createCommon("2024-01-01T00:00:00Z", 100),
            candleFactory.createCommon("2024-01-01T00:01:00Z", 100),
            candleFactory.createCommon("2024-01-02T00:00:00Z", 100),
            candleFactory.createCommon("2024-01-03T00:00:00Z", 100),
            candleFactory.createCommon("2024-01-03T00:01:00Z", 100)
        );

        UpsideCalculator mockCalculator = mock(UpsideCalculator.class);
        CalendarPeriodFilterDecorator decorator = CalendarPeriodFilterDecorator.ofLastDays(2, zone, mockCalculator);

        decorator.calculate(candles);
        verify(mockCalculator).calculate(candles.subList(2, 5));
    }

    /**
     * Проверяем работу с Period.ofMonths(1) — последние 30+ дней (зависит от месяца)
     */
    @Test
    void should_FilterLastMonth() {
        CandleFactory candleFactory = new CandleFactory("TEST");
        List<Candle> candles = List.of(
            candleFactory.createCommon("2024-01-01T00:00:00Z", 100),
            candleFactory.createCommon("2024-01-01T00:01:00Z", 100),
            candleFactory.createCommon("2024-01-02T00:00:00Z", 100),
            candleFactory.createCommon("2024-02-03T00:00:00Z", 100),
            candleFactory.createCommon("2024-02-03T00:01:00Z", 100),
            candleFactory.createCommon("2024-02-03T00:01:00Z", 100),
            candleFactory.createCommon("2024-02-03T00:01:00Z", 100)
        );

        UpsideCalculator mockCalculator = mock(UpsideCalculator.class);

        CalendarPeriodFilterDecorator decorator = new CalendarPeriodFilterDecorator(
            mockCalculator, zone, Period.ofMonths(1)
        );

        decorator.calculate(candles);

        verify(mockCalculator).calculate(candles.subList(3, 7));
    }

    /**
     * Пустой список → NEUTRAL, внутренний калькулятор не вызывается
     */
    @Test
    void should_ReturnNeutral_WhenEmptyList() {
        UpsideCalculator mockCalculator = mock(UpsideCalculator.class);
        CalendarPeriodFilterDecorator decorator = CalendarPeriodFilterDecorator.ofLastDays(7, zone, mockCalculator);

        Upside result = decorator.calculate(List.of());

        assertEquals(Upside.NEUTRAL, result);
        verifyNoInteractions(mockCalculator);
    }

    /**
     * null список → NEUTRAL
     */
    @Test
    void should_ReturnNeutral_WhenNullList() {
        UpsideCalculator mockCalculator = mock(UpsideCalculator.class);
        CalendarPeriodFilterDecorator decorator = CalendarPeriodFilterDecorator.ofLastDays(7, zone, mockCalculator);

        Upside result = decorator.calculate(null);

        assertEquals(Upside.NEUTRAL, result);
        verifyNoInteractions(mockCalculator);
    }

    /**
     * Период 0 дней → пустой результат
     */
    @Test
    void should_HandleZeroPeriod() {
        CandleFactory candleFactory = new CandleFactory("TEST");
        List<Candle> candles = List.of(
            candleFactory.createCommon("2024-01-01T00:00:00Z", 100),
            candleFactory.createCommon("2024-01-01T00:01:00Z", 100),
            candleFactory.createCommon("2024-01-02T00:00:00Z", 100),
            candleFactory.createCommon("2024-01-03T00:00:00Z", 100),
            candleFactory.createCommon("2024-01-03T00:01:00Z", 100)
        );

        UpsideCalculator mockCalculator = mock(UpsideCalculator.class);
        CalendarPeriodFilterDecorator decorator = new CalendarPeriodFilterDecorator(
            mockCalculator, zone, Period.ofDays(0)
        );

        decorator.calculate(candles);

        verify(mockCalculator, atMostOnce()).calculate(List.of());
    }
}
