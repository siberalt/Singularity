package com.siberalt.singularity.strategy.upside;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.indicator.IncrementalRsi;

import java.util.List;

/**
 * Калькулятор Upside на основе индикатора RSI (Relative Strength Index).
 * <p>
 * Сигнал = clamp( (RSI - 50) / 50 , -1 , 1 )
 * Сила (strength) = abs(RSI - 50) / 50   (ограничена 1)
 * </p>
 * <p>
 * RSI вычисляется по закрытию свечей - {@link IncrementalRsi}, один расчёт для всего проекта.
 * Для первой свечи (недостаточно данных) возвращается Upside.NEUTRAL.
 * </p>
 */
public class RSIUpsideCalculator implements UpsideCalculator {

    private final int period;                // период RSI (обычно 14)
    private final double oversoldThreshold;  // порог перепроданности (например 30)
    private final double overboughtThreshold;// порог перекупленности (например 70)

    /**
     * Конструктор с полной настройкой.
     *
     * @param period              период RSI (>= 2)
     * @param oversoldThreshold   уровень перепроданности (0-100)
     * @param overboughtThreshold уровень перекупленности (0-100)
     */
    public RSIUpsideCalculator(int period, double oversoldThreshold, double overboughtThreshold) {
        if (period < 2) throw new IllegalArgumentException("Period must be at least 2");
        this.period = period;
        this.oversoldThreshold = Math.max(0, Math.min(100, oversoldThreshold));
        this.overboughtThreshold = Math.max(0, Math.min(100, overboughtThreshold));
    }

    /**
     * Конструктор со стандартными порогами 30/70.
     */
    public RSIUpsideCalculator(int period) {
        this(period, 30, 70);
    }

    /**
     * Конструктор со стандартными настройками: период 14, пороги 30/70.
     */
    public RSIUpsideCalculator() {
        this(14, 30.0, 70.0);
    }

    @Override
    public Upside calculate(List<Candle> lastCandles) {
        if (lastCandles == null || lastCandles.size() < period + 1) {
            return Upside.NEUTRAL;
        }

        double rsi = IncrementalRsi.of(lastCandles, period);

        if (Double.isNaN(rsi)) {
            return Upside.NEUTRAL;
        }

        // Нормализуем сигнал в [-1, 1]
        double signal = (rsi - 50.0) / 50.0;
        signal = Math.max(-1.0, Math.min(1.0, signal));

        // Сила = удалённость RSI от 50 (0..1)
        double strength = Math.abs(rsi - 50.0) / 50.0;
        strength = Math.min(1.0, strength);

        // Дополнительно можно усилить силу при пересечении экстремальных уровней
        if (rsi <= oversoldThreshold || rsi >= overboughtThreshold) {
            strength = Math.min(1.0, strength + 0.2);
        }

        return new Upside(signal, strength);
    }
}
