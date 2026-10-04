package com.siberalt.singularity.strategy.upside.trend;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.indicator.Ema;
import com.siberalt.singularity.strategy.indicator.Sma;
import com.siberalt.singularity.strategy.upside.Upside;
import com.siberalt.singularity.strategy.upside.UpsideCalculator;

import java.util.List;

/**
 * Пересечение быстрой и медленной средней как состояние: 1 - быстрая выше, -1 - ниже.
 * <p>
 * Это золотой крест из {@code docs/signals.md}, вынесенный из студии в калькулятор. Правило там читается
 * так: держим позицию, пока SMA(50) выше SMA(200), и выходим, когда ниже, - то есть сигналом является
 * <b>сторона</b>, а не величина разрыва.
 * <p>
 * Чем он отличается от {@link EmaSpreadTrendUpsideCalculator}, у которого вход тот же, - и почему это не
 * дубликат. Тот возвращает {@code tanh(spread / typicalSpread)}, то есть меряет <i>силу</i> тренда: едва
 * разошедшиеся средние дают слабый сигнал. Для правила на пересечении это неверно: позиция либо есть, либо
 * нет, и стратегия с порогом покупки 0.9 на свежем кресте просто не купила бы. Поэтому сигнал здесь
 * ступенька, а величина разрыва отдана в {@code strength} - кому нужна сила, тот прочитает её там.
 * <p>
 * Какая именно средняя считается - дело вызывающего: {@link Average#SMA}, {@link Average#EMA} или что угодно
 * своё. Так пересечение SMA и пересечение EMA оказываются одним калькулятором с разным параметром, а не
 * двумя классами, и измеренные на стенде варианты сравниваются без переписывания стратегии.
 * <p>
 * Мёртвая зона у разрыва есть и по той же причине, что у соседей: без неё около нуля сигнал мерцал бы между
 * покупкой и продажей на каждом баре. Пока свечей меньше медленного периода, возвращается
 * {@link Upside#NEUTRAL}.
 */
public class MovingAverageCrossUpsideCalculator implements UpsideCalculator {
    /** По умолчанию мёртвой зоны нет: пересечение есть пересечение. */
    public static final double DEFAULT_FLAT_THRESHOLD = 0;

    /** Чем считать среднюю - то, что делает этот калькулятор общим. */
    public interface Average {
        /** Значения средней по каждой свече; NaN, пока средняя не набрала период. */
        double[] of(List<Candle> candles, int period);

        Average SMA = (candles, period) -> Sma.seriesOf(candles, period, Candle::getCloseAsDouble);

        Average EMA = (candles, period) -> Ema.seriesOf(candles, period, Candle::getCloseAsDouble);
    }

    private final Average average;
    private final int fastPeriod;
    private final int slowPeriod;
    private final double flatThreshold;

    /**
     * @param average       чем считать среднюю
     * @param fastPeriod    период быстрой средней (>= 1, меньше медленного)
     * @param slowPeriod    период медленной средней
     * @param flatThreshold относительный разрыв, ниже которого рынок считается флэтом (>= 0)
     */
    public MovingAverageCrossUpsideCalculator(
        Average average,
        int fastPeriod,
        int slowPeriod,
        double flatThreshold
    ) {
        if (average == null) {
            throw new IllegalArgumentException("Нужно, чем считать среднюю");
        }

        if (fastPeriod < 1 || fastPeriod >= slowPeriod) {
            throw new IllegalArgumentException(
                "The fast period must be positive and less than the slow one, got "
                    + fastPeriod + " and " + slowPeriod);
        }

        if (!(flatThreshold >= 0)) {
            throw new IllegalArgumentException(
                "The flat threshold must not be negative, got " + flatThreshold);
        }

        this.average = average;
        this.fastPeriod = fastPeriod;
        this.slowPeriod = slowPeriod;
        this.flatThreshold = flatThreshold;
    }

    public MovingAverageCrossUpsideCalculator(Average average, int fastPeriod, int slowPeriod) {
        this(average, fastPeriod, slowPeriod, DEFAULT_FLAT_THRESHOLD);
    }

    /** Золотой крест, как он измерен на стенде: простые средние на 50 и 200. */
    public static MovingAverageCrossUpsideCalculator goldenCross() {
        return new MovingAverageCrossUpsideCalculator(Average.SMA, 50, 200);
    }

    public static MovingAverageCrossUpsideCalculator ofSma(int fastPeriod, int slowPeriod) {
        return new MovingAverageCrossUpsideCalculator(Average.SMA, fastPeriod, slowPeriod);
    }

    public static MovingAverageCrossUpsideCalculator ofEma(int fastPeriod, int slowPeriod) {
        return new MovingAverageCrossUpsideCalculator(Average.EMA, fastPeriod, slowPeriod);
    }

    @Override
    public Upside calculate(List<Candle> lastCandles) {
        if (lastCandles == null || lastCandles.size() < slowPeriod) {
            return Upside.NEUTRAL;
        }

        double[] fast = average.of(lastCandles, fastPeriod);
        double[] slow = average.of(lastCandles, slowPeriod);
        double above = fast[fast.length - 1];
        double below = slow[slow.length - 1];

        if (Double.isNaN(above) || Double.isNaN(below) || below == 0) {
            return Upside.NEUTRAL;
        }

        double spread = (above - below) / below;

        if (Math.abs(spread) < flatThreshold) {
            return Upside.NEUTRAL;
        }

        return new Upside(Math.signum(spread), Math.abs(spread));
    }
}
