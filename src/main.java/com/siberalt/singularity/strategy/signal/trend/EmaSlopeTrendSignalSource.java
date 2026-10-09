package com.siberalt.singularity.strategy.signal.trend;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.indicator.Ema;
import com.siberalt.singularity.strategy.signal.Signal;
import com.siberalt.singularity.strategy.signal.SignalSource;

import java.util.List;

/**
 * Калькулятор тренда по наклону одной EMA: 1 - сильный восходящий тренд, -1 - сильный нисходящий,
 * 0 - флэт.
 * <p>
 * slope = (EMA_now - EMA_lookback_назад) / EMA_now;
 * сигнал = tanh( slope / typicalSlope ), сила (strength) = abs(slope).
 * При abs(slope) &lt; flatThreshold возвращается Signal.NEUTRAL.
 * </p>
 * <p>
 * Реагирует быстрее разности двух EMA ({@link EmaSpreadTrendSignalSource}), но наклон по двум
 * точкам чувствительнее к шуму. Пока свечей меньше period + lookback, возвращается Signal.NEUTRAL.
 * </p>
 */
public class EmaSlopeTrendSignalSource implements SignalSource {
    public static final double DEFAULT_TYPICAL_SLOPE = 0.001;
    public static final double DEFAULT_FLAT_THRESHOLD = 0.0001;

    private final int period;
    private final int lookback;
    private final double typicalSlope;
    private final double flatThreshold;

    /**
     * @param period        период EMA (>= 1)
     * @param lookback      на сколько баров назад считать наклон (>= 1)
     * @param typicalSlope  относительный наклон, которому соответствует "сильный" тренд (> 0)
     * @param flatThreshold относительный наклон, ниже которого рынок считается флэтом (>= 0)
     */
    public EmaSlopeTrendSignalSource(int period, int lookback, double typicalSlope, double flatThreshold) {
        if (period < 1) {
            throw new IllegalArgumentException("The EMA period must be positive, got " + period);
        }

        if (lookback < 1) {
            throw new IllegalArgumentException("The lookback must be positive, got " + lookback);
        }

        if (!(typicalSlope > 0)) {
            throw new IllegalArgumentException("The typical slope must be positive, got " + typicalSlope);
        }

        if (!(flatThreshold >= 0)) {
            throw new IllegalArgumentException("The flat threshold must not be negative, got " + flatThreshold);
        }

        this.period = period;
        this.lookback = lookback;
        this.typicalSlope = typicalSlope;
        this.flatThreshold = flatThreshold;
    }

    public EmaSlopeTrendSignalSource(int period, int lookback) {
        this(period, lookback, DEFAULT_TYPICAL_SLOPE, DEFAULT_FLAT_THRESHOLD);
    }

    public EmaSlopeTrendSignalSource() {
        this(50, 10);
    }

    @Override
    public Signal calculate(List<Candle> candles) {
        if (candles == null || candles.size() < period + lookback) {
            return Signal.NEUTRAL;
        }

        double[] ema = Ema.seriesOf(candles, period, Candle::getCloseAsDouble);
        double now = ema[ema.length - 1];
        double before = ema[ema.length - 1 - lookback];

        if (Double.isNaN(now) || Double.isNaN(before) || now == 0) {
            return Signal.NEUTRAL;
        }

        double slope = (now - before) / now;

        if (Math.abs(slope) < flatThreshold) {
            return Signal.NEUTRAL;
        }

        return new Signal(Math.tanh(slope / typicalSlope), Math.abs(slope));
    }
}
