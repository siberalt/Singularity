package com.siberalt.singularity.strategy.upside.subrange;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.upside.Upside;
import com.siberalt.singularity.strategy.upside.UpsideCalculator;

import java.time.*;
import java.util.List;
import java.util.stream.Collectors;

public class CalendarPeriodFilterDecorator implements UpsideCalculator {
    private final UpsideCalculator baseUpsideCalculator;
    private final ZoneId exchangeZone;
    private final Period period;      // для календарных окон (годы, месяцы, дни)

    // Конструктор для Period (календарные дни, месяцы, годы)
    public CalendarPeriodFilterDecorator(UpsideCalculator baseUpsideCalculator, ZoneId exchangeZone, Period period) {
        this.baseUpsideCalculator = baseUpsideCalculator;
        this.exchangeZone = exchangeZone;
        this.period = period;
    }

    @Override
    public Upside calculate(List<Candle> candles) {
        if (candles == null || candles.isEmpty()) {
            return Upside.NEUTRAL;
        }

        List<Candle> filteredCandles = filterByPeriod(candles);

        if (filteredCandles.isEmpty()) {
            return Upside.NEUTRAL;
        }

        return baseUpsideCalculator.calculate(filteredCandles);
    }

    /**
     * Фильтрация по календарному периоду (Period)
     * Пример: последние 2 месяца, последний 1 год и т.д.
     */
    private List<Candle> filterByPeriod(List<Candle> candles) {
        Instant lastTime = candles.get(candles.size() - 1).getTime();
        LocalDate referenceDate = lastTime.atZone(exchangeZone).toLocalDate().plusDays(1);

        // Определяем начало периода
        LocalDate startDate = referenceDate.minus(period);
        LocalDate endDate = referenceDate;

        // Для периода в днях/месяцах/годах - берем полные календарные интервалы
        Instant startInstant = startDate.atStartOfDay(exchangeZone).toInstant();
        Instant endInstant = endDate.atStartOfDay(exchangeZone).toInstant();

        return candles
            .stream()
            .filter(c -> belongsToPeriod(c, startInstant, endInstant))
            .collect(Collectors.toList());
    }

    private boolean belongsToPeriod(Candle candle, Instant start, Instant end) {
        Instant time = candle.getTime().atZone(exchangeZone).toInstant();

        return !time.isBefore(start) && time.isBefore(end);
    }

    public static CalendarPeriodFilterDecorator ofLastDays(
        int daysCount,
        ZoneId zoneId,
        UpsideCalculator baseUpsideCalculator
    ){
        return new CalendarPeriodFilterDecorator(baseUpsideCalculator, zoneId, Period.ofDays(daysCount));
    }

    public static CalendarPeriodFilterDecorator ofLastDays(
        int daysCount,
        UpsideCalculator baseUpsideCalculator
    ){
        return new CalendarPeriodFilterDecorator(baseUpsideCalculator, ZoneOffset.UTC, Period.ofDays(daysCount));
    }
}
