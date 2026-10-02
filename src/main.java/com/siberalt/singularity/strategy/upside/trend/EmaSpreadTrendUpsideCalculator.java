package com.siberalt.singularity.strategy.upside.trend;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.indicator.Ema;
import com.siberalt.singularity.strategy.upside.Upside;
import com.siberalt.singularity.strategy.upside.UpsideCalculator;

import java.util.List;

/**
 * Калькулятор тренда по разности быстрой и медленной EMA: 1 - сильный восходящий тренд,
 * -1 - сильный нисходящий, 0 - флэт.
 * <p>
 * spread = (EMA_fast - EMA_slow) / EMA_slow;
 * сигнал = tanh( spread / typicalSpread ), сила (strength) = abs(spread).
 * При abs(spread) &lt; flatThreshold возвращается Upside.NEUTRAL: у tanh ноль только при нулевой
 * разности, а реальный рынок почти никогда в нём не бывает, без мёртвой зоны флэт мерцал бы около нуля.
 * </p>
 * <p>
 * Разность двух EMA сглаживает шум лучше наклона одной, но разворот показывает позже.
 * Пока свечей меньше медленного периода, возвращается Upside.NEUTRAL.
 * </p>
 */
public class EmaSpreadTrendUpsideCalculator implements UpsideCalculator {
    public static final double DEFAULT_TYPICAL_SPREAD = 0.002;
    public static final double DEFAULT_FLAT_THRESHOLD = 0.0002;

    private final int fastPeriod;
    private final int slowPeriod;
    private final double typicalSpread;
    private final double flatThreshold;

    /**
     * @param fastPeriod    период быстрой EMA (>= 1, меньше медленного)
     * @param slowPeriod    период медленной EMA
     * @param typicalSpread относительная разность EMA, которой соответствует "сильный" тренд (> 0)
     * @param flatThreshold относительная разность, ниже которой рынок считается флэтом (>= 0)
     */
    public EmaSpreadTrendUpsideCalculator(
        int fastPeriod,
        int slowPeriod,
        double typicalSpread,
        double flatThreshold
    ) {
        if (fastPeriod < 1 || fastPeriod >= slowPeriod) {
            throw new IllegalArgumentException(
                "The fast period must be positive and less than the slow one, got " + fastPeriod + " and " + slowPeriod
            );
        }

        if (!(typicalSpread > 0)) {
            throw new IllegalArgumentException("The typical spread must be positive, got " + typicalSpread);
        }

        if (!(flatThreshold >= 0)) {
            throw new IllegalArgumentException("The flat threshold must not be negative, got " + flatThreshold);
        }

        this.fastPeriod = fastPeriod;
        this.slowPeriod = slowPeriod;
        this.typicalSpread = typicalSpread;
        this.flatThreshold = flatThreshold;
    }

    public EmaSpreadTrendUpsideCalculator(int fastPeriod, int slowPeriod) {
        this(fastPeriod, slowPeriod, DEFAULT_TYPICAL_SPREAD, DEFAULT_FLAT_THRESHOLD);
    }

    public EmaSpreadTrendUpsideCalculator() {
        this(20, 50);
    }

    @Override
    public Upside calculate(List<Candle> candles) {
        if (candles == null || candles.size() < slowPeriod) {
            return Upside.NEUTRAL;
        }

        double fast = Ema.of(candles, fastPeriod);
        double slow = Ema.of(candles, slowPeriod);

        if (Double.isNaN(fast) || Double.isNaN(slow) || slow == 0) {
            return Upside.NEUTRAL;
        }

        double spread = (fast - slow) / slow;

        if (Math.abs(spread) < flatThreshold) {
            return Upside.NEUTRAL;
        }

        return new Upside(Math.tanh(spread / typicalSpread), Math.abs(spread));
    }
}
