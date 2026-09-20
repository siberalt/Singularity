package com.siberalt.singularity.strategy.volatility;

import com.siberalt.singularity.entity.candle.Candle;

/**
 * ATR Уайлдера, принимающий бары по одному и помнящий, на чём остановился.
 * <p>
 * Тот же расчёт, что и в {@link ATRVolatilityCalculator}, но без пересчёта с начала: первые
 * {@code period} истинных диапазонов складываются в среднее, дальше каждый следующий сглаживается с
 * накопленным. Это нужно там, где волатильность требуется не один раз на окно, а у каждого бара:
 * прежний расчёт стоил бы O(n) на бар, этот - O(1).
 * <p>
 * Состояние - предыдущее закрытие, сколько диапазонов уже пришло и само значение; объект считает один
 * ряд баров и не переиспользуется для другого.
 */
public class IncrementalATR {
    private final int period;
    private double previousClose;
    private boolean started;
    private int trueRanges;
    private double value;

    public IncrementalATR(int period) {
        if (period < 1) {
            throw new IllegalArgumentException("Период ATR должен быть положительным, получено " + period);
        }

        this.period = period;
    }

    public IncrementalATR() {
        this(14);
    }

    /**
     * Добавляет бар и возвращает волатильность после него. Первый бар только запоминает закрытие -
     * истинный диапазон меряется относительно предыдущего, а до него ничего нет.
     */
    public double add(Candle candle) {
        if (!started) {
            previousClose = candle.getCloseAsDouble();
            started = true;

            return value();
        }

        double high = candle.getHighAsDouble();
        double low = candle.getLowAsDouble();
        double trueRange = Math.max(high - low, Math.max(Math.abs(high - previousClose), Math.abs(low - previousClose)));

        previousClose = candle.getCloseAsDouble();
        trueRanges++;

        // Первые period диапазонов - простое среднее, которое набирается по слагаемому за бар; дальше
        // сглаживание Уайлдера.
        value = trueRanges <= period ? value + trueRange / period : (value * (period - 1) + trueRange) / period;

        return value();
    }

    /** Ноль, пока баров меньше периода: мерить ещё нечем, и лучше сказать об этом, чем выдать полумеру. */
    public double value() {
        return ready() ? value : 0;
    }

    public boolean ready() {
        return trueRanges >= period;
    }
}
