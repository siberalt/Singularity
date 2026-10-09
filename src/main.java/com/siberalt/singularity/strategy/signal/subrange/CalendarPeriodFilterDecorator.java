package com.siberalt.singularity.strategy.signal.subrange;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.signal.Signal;
import com.siberalt.singularity.strategy.signal.SignalSource;

import java.time.*;
import java.util.List;
import java.util.stream.Collectors;

public class CalendarPeriodFilterDecorator implements SignalSource {
    private final SignalSource baseSignalSource;
    private final ZoneId exchangeZone;
    private final Period period;      // для календарных окон (годы, месяцы, дни)

    // Конструктор для Period (календарные дни, месяцы, годы)
    public CalendarPeriodFilterDecorator(SignalSource baseSignalSource, ZoneId exchangeZone, Period period) {
        this.baseSignalSource = baseSignalSource;
        this.exchangeZone = exchangeZone;
        this.period = period;
    }

    @Override
    public Signal calculate(List<Candle> candles) {
        if (candles == null || candles.isEmpty()) {
            return Signal.NEUTRAL;
        }

        List<Candle> filteredCandles = filterByPeriod(candles);

        if (filteredCandles.isEmpty()) {
            return Signal.NEUTRAL;
        }

        return baseSignalSource.calculate(filteredCandles);
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
        SignalSource baseSignalSource
    ){
        return new CalendarPeriodFilterDecorator(baseSignalSource, zoneId, Period.ofDays(daysCount));
    }

    public static CalendarPeriodFilterDecorator ofLastDays(
        int daysCount,
        SignalSource baseSignalSource
    ){
        return new CalendarPeriodFilterDecorator(baseSignalSource, ZoneOffset.UTC, Period.ofDays(daysCount));
    }
}
